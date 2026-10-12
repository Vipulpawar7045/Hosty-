package com.minivercel.function;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.minivercel.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionUrlAuthType;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;

/**
 * Deploys a Node.js serverless function to AWS Lambda and exposes it via a Function URL.
 *
 * <p>Pipeline: detect {@code api/} or {@code functions/} → {@code npm install --omit=dev}
 * (if a {@code package.json} is present) → zip → create-or-update the Lambda → ensure a
 * Function URL and, for {@code NONE} auth, a public-invoke permission.
 *
 * <p>The caller supplies the final function name (production {@code mv-<projectId>}, previews
 * {@code mv-<projectId>-<branchSlug>}), so repeated deploys of the same target update one
 * function and keep a stable URL rather than piling up new ones.
 */
@Component
public class LambdaFunctionDeployer implements FunctionDeployer {

	private static final Logger log = LoggerFactory.getLogger(LambdaFunctionDeployer.class);

	/** Directories, in priority order, that mark a repo as having backend functions. */
	private static final List<String> FUNCTION_DIRS = List.of("api", "functions");

	/** Statement id for the resource-based permission that makes a NONE-auth URL public. */
	private static final String PUBLIC_INVOKE_STATEMENT_ID = "mv-public-invoke-url";

	private final LambdaClient lambda;
	private final AppProperties props;

	public LambdaFunctionDeployer(LambdaClient lambda, AppProperties props) {
		this.lambda = lambda;
		this.props = props;
	}

	@Override
	public FunctionDeployResult deploy(Path repoDir, String functionName, StringBuilder logSink) {
		AppProperties.Lambda cfg = (props.aws() != null) ? props.aws().lambda() : null;
		if (cfg == null || cfg.roleArn() == null || cfg.roleArn().isBlank()) {
			return FunctionDeployResult.skipped("No Lambda role configured — static-only deploy");
		}

		Path functionDir = detectFunctionDir(repoDir);
		if (functionDir == null) {
			return FunctionDeployResult.skipped("No api/ or functions/ directory — static-only deploy");
		}

		String name = sanitizeName(functionName);
		logSink.append("\n── Serverless function ──\n");
		logSink.append("Detected function directory: ")
				.append(repoDir.relativize(functionDir)).append("\n");

		try {
			installDependencies(functionDir, logSink);

			byte[] zip = zipDirectory(functionDir);
			logSink.append("Packaged function: ").append(zip.length).append(" bytes\n");

			deployToLambda(name, zip, cfg, logSink);
			String url = ensureFunctionUrl(name, cfg, logSink);

			logSink.append("Function URL: ").append(url).append("\n");
			return FunctionDeployResult.deployed(name, url);
		}
		catch (Exception ex) {
			log.error("Function deployment failed for function {}", name, ex);
			logSink.append("Function deployment FAILED: ").append(ex.getMessage()).append("\n");
			return FunctionDeployResult.failed("Function deployment failed: " + ex.getMessage());
		}
	}

	@Override
	public void remove(String functionName) {
		String name = sanitizeName(functionName);
		try {
			lambda.deleteFunction(DeleteFunctionRequest.builder().functionName(name).build());
			log.info("Deleted Lambda function {}", name);
		}
		catch (ResourceNotFoundException ex) {
			// Already gone (never deployed, or removed by an earlier teardown) — nothing to do.
		}
	}

	// ── Detection & packaging ───────────────────────────────────────────────────────

	private static Path detectFunctionDir(Path repoDir) {
		if (repoDir == null) return null;
		for (String candidate : FUNCTION_DIRS) {
			Path dir = repoDir.resolve(candidate);
			if (Files.isDirectory(dir)) {
				return dir;
			}
		}
		return null;
	}

	/** Runs {@code npm install --omit=dev} when the function has a package.json. */
	private void installDependencies(Path functionDir, StringBuilder logSink)
			throws IOException, InterruptedException {
		if (!Files.isRegularFile(functionDir.resolve("package.json"))) {
			logSink.append("No package.json — skipping dependency install\n");
			return;
		}
		logSink.append("Installing production dependencies...\n");
		runProcess(shellCommand("npm install --omit=dev"), functionDir, logSink);
	}

	// ── Lambda API ──────────────────────────────────────────────────────────────────

	/** Creates the function if absent, otherwise updates its code; blocks until ready. */
	private void deployToLambda(String name, byte[] zip, AppProperties.Lambda cfg, StringBuilder logSink) {
		SdkBytes code = SdkBytes.fromByteArray(zip);
		if (functionExists(name)) {
			logSink.append("Updating existing Lambda function: ").append(name).append("\n");
			lambda.updateFunctionCode(UpdateFunctionCodeRequest.builder()
					.functionName(name)
					.zipFile(code)
					.build());
			lambda.waiter().waitUntilFunctionUpdatedV2(
					GetFunctionRequest.builder().functionName(name).build());
		}
		else {
			logSink.append("Creating Lambda function: ").append(name).append("\n");
			lambda.createFunction(CreateFunctionRequest.builder()
					.functionName(name)
					.runtime(cfg.runtime())
					.role(cfg.roleArn())
					.handler(cfg.handler())
					.code(FunctionCode.builder().zipFile(code).build())
					.timeout(cfg.timeoutSeconds())
					.memorySize(cfg.memoryMb())
					.build());
			lambda.waiter().waitUntilFunctionActiveV2(
					GetFunctionRequest.builder().functionName(name).build());
		}
	}

	private boolean functionExists(String name) {
		try {
			lambda.getFunction(GetFunctionRequest.builder().functionName(name).build());
			return true;
		}
		catch (ResourceNotFoundException ex) {
			return false;
		}
	}

	/** Returns the function's public URL, creating the URL config on first deploy. */
	private String ensureFunctionUrl(String name, AppProperties.Lambda cfg, StringBuilder logSink) {
		FunctionUrlAuthType authType = "AWS_IAM".equalsIgnoreCase(cfg.urlAuthType())
				? FunctionUrlAuthType.AWS_IAM
				: FunctionUrlAuthType.NONE;
		try {
			String url = lambda.getFunctionUrlConfig(
					GetFunctionUrlConfigRequest.builder().functionName(name).build()).functionUrl();
			logSink.append("Reusing existing Function URL\n");
			ensureInvokePermission(name, authType, logSink);
			return url;
		}
		catch (ResourceNotFoundException ex) {
			String url = lambda.createFunctionUrlConfig(CreateFunctionUrlConfigRequest.builder()
					.functionName(name)
					.authType(authType)
					.build()).functionUrl();
			logSink.append("Created Function URL (auth: ").append(authType).append(")\n");
			ensureInvokePermission(name, authType, logSink);
			return url;
		}
	}

	/**
	 * A {@code NONE}-auth Function URL still returns 403 until a resource-based policy
	 * allows anonymous {@code lambda:InvokeFunctionUrl}. This grants that once.
	 */
	private void ensureInvokePermission(String name, FunctionUrlAuthType authType, StringBuilder logSink) {
		if (authType != FunctionUrlAuthType.NONE) {
			return;
		}
		try {
			lambda.addPermission(AddPermissionRequest.builder()
					.functionName(name)
					.statementId(PUBLIC_INVOKE_STATEMENT_ID)
					.action("lambda:InvokeFunctionUrl")
					.principal("*")
					.functionUrlAuthType(FunctionUrlAuthType.NONE)
					.build());
			logSink.append("Granted public invoke permission\n");
		}
		catch (ResourceConflictException ex) {
			// Permission already granted on an earlier deploy — nothing to do.
		}

		try {
			lambda.addPermission(AddPermissionRequest.builder()
					.functionName(name)
					.statementId(PUBLIC_INVOKE_STATEMENT_ID + "-fn")
					.action("lambda:InvokeFunction")
					.principal("*")
					.build());
		}
		catch (ResourceConflictException ex) {
			// Permission already granted on an earlier deploy — nothing to do.
		}
	}

	// ── Helpers ─────────────────────────────────────────────────────────────────────

	/** Zips a directory in memory, storing entries with forward-slash relative paths. */
	private static byte[] zipDirectory(Path dir) throws IOException {
		var baos = new ByteArrayOutputStream();
		try (var zos = new ZipOutputStream(baos); var files = Files.walk(dir)) {
			files.filter(Files::isRegularFile).forEach(file -> {
				String entryName = dir.relativize(file).toString().replace('\\', '/');
				try {
					zos.putNextEntry(new ZipEntry(entryName));
					Files.copy(file, zos);
					zos.closeEntry();
				}
				catch (IOException ex) {
					throw new RuntimeException("Failed to zip " + entryName, ex);
				}
			});
		}
		return baos.toByteArray();
	}

	/** {@code cmd /c <command>} on Windows (for npm's .cmd shim), raw tokens elsewhere. */
	private static List<String> shellCommand(String command) {
		boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
		if (isWindows) {
			return List.of("cmd", "/c", command);
		}
		return List.of(command.split("\\s+"));
	}

	private void runProcess(List<String> command, Path workingDir, StringBuilder logSink)
			throws IOException, InterruptedException {
		logSink.append("$ ").append(String.join(" ", command)).append("\n");

		Process process = new ProcessBuilder(command)
				.directory(workingDir.toFile())
				.redirectErrorStream(true)
				.start();

		String output = new String(process.getInputStream().readAllBytes());
		logSink.append(output);

		boolean finished = process.waitFor(5, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
			throw new IOException("Timed out running: " + String.join(" ", command));
		}
		if (process.exitValue() != 0) {
			throw new IOException("Command exited with code " + process.exitValue()
					+ ": " + String.join(" ", command));
		}
	}

	/** Lambda names allow only {@code [a-zA-Z0-9-_]} and cap at 64 chars. */
	private static String sanitizeName(String raw) {
		String cleaned = raw.replaceAll("[^a-zA-Z0-9-_]", "-");
		return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
	}
}
