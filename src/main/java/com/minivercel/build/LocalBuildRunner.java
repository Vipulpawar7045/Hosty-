package com.minivercel.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.minivercel.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Phase-2 build runner: clones a Git repo and runs the build command on this machine.
 *
 * <p>Phase 3 will swap this out for a CodeBuild-backed implementation without changing
 * any calling code.
 *
 * <p>Security gates applied before any shell is touched:
 * <ul>
 *   <li>Repo host must be in {@code minivercel.build.allowed-git-hosts}</li>
 *   <li>Build command must be in {@code minivercel.build.allowed-build-commands}</li>
 *   <li>Total artifact size is checked against {@code minivercel.build.max-artifact-bytes}</li>
 * </ul>
 */
@Component
public class LocalBuildRunner implements BuildRunner {

	private static final Logger log = LoggerFactory.getLogger(LocalBuildRunner.class);

	private final AppProperties props;

	public LocalBuildRunner(AppProperties props) {
		this.props = props;
	}

	@Override
	public BuildResult run(BuildRequest request, Path workDir) {
		var sb = new StringBuilder();

		try {
			validateRequest(request);

			// ── 1. Clone ─────────────────────────────────────────────────────────────
			// When a branch is given, clone it directly: a depth-1 clone of the default
			// branch would not contain a feature branch's commit, so previews need this.
			Path repoDir = workDir.resolve("repo");
			List<String> cloneCmd = new java.util.ArrayList<>(
					List.of("git", "clone", "--depth", "1"));
			if (request.branch() != null && !request.branch().isBlank()) {
				cloneCmd.add("--branch");
				cloneCmd.add(request.branch());
			}
			cloneCmd.add(request.repoUrl());
			cloneCmd.add(repoDir.toString());

			BuildResult cloneResult = runProcess(cloneCmd, workDir, sb);
			if (!cloneResult.success()) {
				return cloneResult;
			}

			// Optional: check out a specific commit (for preview deployments later)
			if (request.commitSha() != null && !request.commitSha().isBlank()) {
				BuildResult checkoutResult = runProcess(
						List.of("git", "checkout", request.commitSha()),
						repoDir, sb);
				if (!checkoutResult.success()) {
					return checkoutResult;
				}
			}

			// ── 2. Build ──────────────────────────────────────────────────────────────
			// On Windows, ProcessBuilder cannot resolve .cmd shims (npm, yarn, pnpm)
			// unless we delegate to cmd.exe. Wrapping with "cmd /c" fixes that.
			List<String> cmd = buildCommand(request.buildCommand());
			BuildResult buildResult = runProcess(cmd, repoDir, sb);
			if (!buildResult.success()) {
				return buildResult;
			}

			// ── 3. Validate artifact ─────────────────────────────────────────────────
			Path artifactDir = repoDir.resolve(request.outputDir());
			if (!Files.isDirectory(artifactDir)) {
				return BuildResult.failure(sb.toString(),
						"Output directory not found after build: " + request.outputDir());
			}

			long totalBytes = totalSize(artifactDir);
			long maxBytes = props.build().maxArtifactBytes();
			if (totalBytes > maxBytes) {
				return BuildResult.failure(sb.toString(),
						"Artifact size %d bytes exceeds limit %d bytes".formatted(totalBytes, maxBytes));
			}

			log.info("Build succeeded, artifact at {}, size {} bytes", artifactDir, totalBytes);
			return BuildResult.success(artifactDir, repoDir, sb.toString());

		}
		catch (Exception ex) {
			log.error("Build failed with exception", ex);
			return BuildResult.failure(sb.toString(), ex.getMessage());
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────────

	/**
	 * Builds the command list for ProcessBuilder.
	 *
	 * <p>On Windows, npm/yarn/pnpm are .cmd shims that ProcessBuilder cannot
	 * resolve directly — wrapping with {@code cmd /c} lets the shell find them.
	 * On Unix the raw tokens work as-is.
	 */
	private static List<String> buildCommand(String buildCommand) {
		boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
		if (isWindows) {
			return List.of("cmd", "/c", buildCommand);
		}
		return List.of(buildCommand.split("\\s+"));
	}

	private void validateRequest(BuildRequest request) {
		String host = extractHost(request.repoUrl());
		List<String> allowedHosts = props.build().allowedGitHosts();
		if (allowedHosts != null && !allowedHosts.isEmpty() && !allowedHosts.contains(host)) {
			throw new IllegalArgumentException("Git host not allowed: " + host);
		}

		String cmd = request.buildCommand();
		List<String> allowedCmds = props.build().allowedBuildCommands();
		if (allowedCmds != null && !allowedCmds.isEmpty() && !allowedCmds.contains(cmd)) {
			throw new IllegalArgumentException("Build command not allowed: " + cmd);
		}
	}

	private BuildResult runProcess(List<String> command, Path workingDir, StringBuilder logSink)
			throws IOException, InterruptedException {

		log.debug("Running {} in {}", command, workingDir);
		logSink.append("$ ").append(String.join(" ", command)).append("\n");

		Duration timeout = props.build().timeout();
		long timeoutSeconds = (timeout != null) ? timeout.toSeconds() : 600;

		Process process = new ProcessBuilder(command)
				.directory(workingDir.toFile())
				.redirectErrorStream(true)  // merge stderr into stdout
				.start();

		// Read all output before waiting, to avoid blocking on a full pipe buffer
		String output = new String(process.getInputStream().readAllBytes());
		logSink.append(output);

		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			return BuildResult.failure(logSink.toString(),
					"Build timed out after " + timeoutSeconds + "s");
		}

		int exitCode = process.exitValue();
		if (exitCode != 0) {
			return BuildResult.failure(logSink.toString(),
					"Command exited with code " + exitCode + ": " + String.join(" ", command));
		}

		return BuildResult.success(null, logSink.toString());
	}

	private static String extractHost(String repoUrl) {
		// Handles https://github.com/... → "github.com"
		try {
			return new java.net.URI(repoUrl).getHost();
		}
		catch (Exception ex) {
			return repoUrl;
		}
	}

	private static long totalSize(Path dir) throws IOException {
		try (var stream = Files.walk(dir)) {
			return stream.filter(Files::isRegularFile)
					.mapToLong(p -> {
						try { return Files.size(p); }
						catch (IOException ex) { return 0L; }
					})
					.sum();
		}
	}
}
