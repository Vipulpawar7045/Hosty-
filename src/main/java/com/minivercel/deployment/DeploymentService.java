package com.minivercel.deployment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.minivercel.build.BuildRequest;
import com.minivercel.build.BuildResult;
import com.minivercel.build.BuildRunner;
import com.minivercel.build.S3Publisher;
import com.minivercel.common.BadRequestException;
import com.minivercel.common.NotFoundException;
import com.minivercel.config.AppProperties;
import com.minivercel.deployment.dto.DeploymentResponse;
import com.minivercel.function.FunctionDeployResult;
import com.minivercel.function.FunctionDeployer;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectService;
import com.minivercel.ratelimit.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the clone → build → upload → record pipeline.
 *
 * <p>{@link #triggerAsync} returns immediately after writing a {@code queued} record;
 * the actual work happens on Spring's async executor so the HTTP response is not held open.
 */
@Service
public class DeploymentService {

	private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

	/** How many deployments to return when listing a project's history. */
	private static final int LIST_LIMIT = 20;

	/** Assumed production branch when a project doesn't set one explicitly. */
	private static final String DEFAULT_PRODUCTION_BRANCH = "main";

	static final String ENV_PRODUCTION = "production";
	static final String ENV_PREVIEW = "preview";

	/** How many recent deployments to scan when tearing down a branch's previews. */
	private static final int TEARDOWN_SCAN_LIMIT = 100;

	private final DeploymentRepository deploymentRepository;
	private final ProjectService projectService;
	private final BuildRunner buildRunner;
	private final S3Publisher s3Publisher;
	private final FunctionDeployer functionDeployer;
	private final AppProperties props;
	private final RateLimitService rateLimitService;

	public DeploymentService(DeploymentRepository deploymentRepository,
			ProjectService projectService,
			BuildRunner buildRunner,
			S3Publisher s3Publisher,
			FunctionDeployer functionDeployer,
			AppProperties props,
			RateLimitService rateLimitService) {
		this.deploymentRepository = deploymentRepository;
		this.projectService = projectService;
		this.buildRunner = buildRunner;
		this.s3Publisher = s3Publisher;
		this.functionDeployer = functionDeployer;
		this.props = props;
		this.rateLimitService = rateLimitService;
	}

	// ── Public API ────────────────────────────────────────────────────────────────

	/**
	 * Creates a {@code queued} deployment record and kicks off the build asynchronously.
	 *
	 * @return the initial record — status will be {@code queued} until the build starts
	 */
	public DeploymentResponse enqueue(String projectId, String userId) {
		return enqueue(projectId, userId, null);
	}

	/**
	 * Creates a {@code queued} deployment record and kicks off the build asynchronously.
	 *
	 * @param commitSha optional commit SHA to record with the deployment
	 * @return the initial record — status will be {@code queued} until the build starts
	 */
	public DeploymentResponse enqueue(String projectId, String userId, String commitSha) {
		return enqueue(projectId, userId, null, commitSha);
	}

	/**
	 * Creates a {@code queued} deployment record and kicks off the build asynchronously.
	 *
	 * @param branch    git branch this deploy came from; a branch equal to the project's
	 *                  production branch (or null, for manual triggers) deploys to production,
	 *                  anything else to preview
	 * @param commitSha optional commit SHA to record with the deployment
	 * @return the initial record — status will be {@code queued} until the build starts
	 */
	public DeploymentResponse enqueue(String projectId, String userId, String branch, String commitSha) {
		Project project = projectService.requireOwned(projectId, userId);

		// Check rate limits before creating deployment
		rateLimitService.checkDeploymentLimits(projectId);

		String environment = classify(branch, project.getProductionBranch());

		Deployment deployment = new Deployment();
		deployment.setDeploymentId(UUID.randomUUID().toString());
		deployment.setProjectId(projectId);
		deployment.setUserId(userId);
		deployment.setStatus(DeploymentStatus.QUEUED.value());
		deployment.setBranch(branch);
		deployment.setEnvironment(environment);
		deployment.setCommitSha(commitSha);
		deployment.setCreatedAt(Instant.now().toString());
		deploymentRepository.save(deployment);

		triggerAsync(deployment, project);
		return DeploymentResponse.from(deployment);
	}

	/**
	 * Tears down a preview environment for a branch: deletes its Lambda function, removes its
	 * S3 artifacts, and marks its deployments {@code removed}. Idempotent — safe to call for a
	 * branch that was never previewed or has already been cleaned.
	 *
	 * <p>Production deployments are never touched: only records whose environment is
	 * {@code preview} and whose branch matches are affected.
	 *
	 * @return the number of deployment records marked removed
	 */
	public int removePreview(String projectId, String userId, String branch) {
		projectService.requireOwned(projectId, userId);
		return removePreviewInternal(projectId, branch);
	}

	/**
	 * Same as {@link #removePreview} but without an ownership check — for the webhook path,
	 * which has already matched the project by repo URL and has no authenticated user.
	 */
	public int removePreviewForWebhook(String projectId, String branch) {
		return removePreviewInternal(projectId, branch);
	}

	private int removePreviewInternal(String projectId, String branch) {
		if (branch == null || branch.isBlank()) {
			return 0;
		}

		// 1. Delete the branch-scoped Lambda function (no-op if it was never created).
		try {
			functionDeployer.remove(previewFunctionName(projectId, branch));
		}
		catch (Exception ex) {
			log.warn("Failed to delete preview function for {}#{}", projectId, branch, ex);
		}

		// 2. Delete S3 artifacts and mark matching preview deployments removed.
		int removed = 0;
		String bucket = (props.aws() != null) ? props.aws().artifactBucket() : null;
		for (Deployment d : deploymentRepository.findByProjectId(projectId, TEARDOWN_SCAN_LIMIT)) {
			if (!ENV_PREVIEW.equals(d.getEnvironment()) || !branch.equals(d.getBranch())) {
				continue;
			}
			if (DeploymentStatus.REMOVED.value().equals(d.getStatus())) {
				continue; // already cleaned
			}
			if (bucket != null && !bucket.isBlank()) {
				try {
					s3Publisher.deletePrefix(projectId + "/" + d.getDeploymentId());
				}
				catch (Exception ex) {
					log.warn("Failed to delete S3 artifacts for deployment {}", d.getDeploymentId(), ex);
				}
			}
			d.setStatus(DeploymentStatus.REMOVED.value());
			d.setFinishedAt(Instant.now().toString());
			deploymentRepository.save(d);
			removed++;
		}

		log.info("Removed preview {}#{}: {} deployment(s) cleaned", projectId, branch, removed);
		return removed;
	}

	public DeploymentResponse get(String deploymentId, String userId) {
		return DeploymentResponse.from(requireOwned(deploymentId, userId));
	}

	public List<DeploymentResponse> listForProject(String projectId, String userId) {
		// Ownership check — throws 404 if the project doesn't belong to this user
		projectService.requireOwned(projectId, userId);
		return deploymentRepository.findByProjectId(projectId, LIST_LIMIT)
				.stream()
				.map(DeploymentResponse::from)
				.toList();
	}

	/**
	 * Cancels a deployment if it's still queued or building.
	 *
	 * @param deploymentId the deployment ID
	 * @param userId the user ID (for ownership check)
	 * @param reason the cancellation reason
	 * @return the updated deployment
	 */
	public DeploymentResponse cancel(String deploymentId, String userId, String reason) {
		Deployment deployment = requireOwned(deploymentId, userId);

		String status = deployment.getStatus();
		if (DeploymentStatus.SUCCESS.value().equals(status)
				|| DeploymentStatus.FAILED.value().equals(status)
				|| DeploymentStatus.CANCELLED.value().equals(status)) {
			throw new IllegalStateException("Cannot cancel a deployment that is already " + status);
		}

		deployment.setStatus(DeploymentStatus.CANCELLED.value());
		deployment.setErrorMessage("Cancelled by user: " + reason);
		deployment.setFinishedAt(Instant.now().toString());
		deploymentRepository.save(deployment);

		log.info("Deployment {} cancelled by user {}: {}", deploymentId, userId, reason);
		return DeploymentResponse.from(deployment);
	}

	/**
	 * Instantly rolls back a project to a previous successful deployment.
	 *
	 * <p>Phase 9: Clones the target deployment's artifacts in S3 under a new deployment ID and
	 * records a new SUCCESS deployment pointing to the target's live assets and serverless function,
	 * without performing a slow git clone or re-running the build.
	 *
	 * @param projectId the project ID
	 * @param targetDeploymentId the successful deployment to roll back to
	 * @param userId the requesting user
	 * @return the new deployment representing the active rollback
	 */
	public DeploymentResponse rollback(String projectId, String targetDeploymentId, String userId) {
		projectService.requireOwned(projectId, userId);

		Deployment target = requireOwned(targetDeploymentId, userId);
		if (!projectId.equals(target.getProjectId())) {
			throw new BadRequestException("Deployment " + targetDeploymentId + " does not belong to project " + projectId);
		}
		if (!DeploymentStatus.SUCCESS.value().equals(target.getStatus())) {
			throw new BadRequestException("Cannot roll back to deployment " + targetDeploymentId + " because its status is " + target.getStatus());
		}

		String newDeploymentId = UUID.randomUUID().toString();
		Deployment rollbackDeployment = new Deployment();
		rollbackDeployment.setDeploymentId(newDeploymentId);
		rollbackDeployment.setProjectId(projectId);
		rollbackDeployment.setUserId(userId);
		rollbackDeployment.setBranch(target.getBranch());
		rollbackDeployment.setEnvironment(target.getEnvironment());
		rollbackDeployment.setCommitSha(target.getCommitSha());
		rollbackDeployment.setStatus(DeploymentStatus.SUCCESS.value());
		rollbackDeployment.setCreatedAt(Instant.now().toString());
		rollbackDeployment.setFinishedAt(Instant.now().toString());
		rollbackDeployment.setLambdaFunctionName(target.getLambdaFunctionName());
		rollbackDeployment.setFunctionUrl(target.getFunctionUrl());

		// Clone S3 artifacts or local artifacts to the new deployment prefix
		String bucket = (props.aws() != null) ? props.aws().artifactBucket() : null;
		if (bucket != null && !bucket.isBlank()) {
			try {
				s3Publisher.copyPrefix(projectId + "/" + targetDeploymentId, projectId + "/" + newDeploymentId);
				String base = props.aws().publicBaseUrl();
				if (base != null && !base.isBlank()) {
					rollbackDeployment.setDeployUrl(base.stripTrailing() + "/" + projectId + "/" + newDeploymentId + "/index.html");
				} else {
					rollbackDeployment.setDeployUrl("s3://" + bucket + "/" + projectId + "/" + newDeploymentId + "/");
				}
			} catch (Exception ex) {
				log.warn("S3 rollback copy failed, reusing target deploy URL", ex);
				rollbackDeployment.setDeployUrl(target.getDeployUrl());
			}
		} else {
			String localDir = (props.build() != null) ? props.build().localArtifactDir() : null;
			if (localDir != null && !localDir.isBlank()) {
				Path src = Path.of(localDir, projectId, targetDeploymentId);
				Path dest = Path.of(localDir, projectId, newDeploymentId);
				try {
					if (Files.isDirectory(src)) {
						copyDirectory(src, dest);
						rollbackDeployment.setDeployUrl("file://" + dest.toAbsolutePath());
					} else {
						rollbackDeployment.setDeployUrl(target.getDeployUrl());
					}
				} catch (Exception ex) {
					log.warn("Local artifact rollback copy failed", ex);
					rollbackDeployment.setDeployUrl(target.getDeployUrl());
				}
			} else {
				rollbackDeployment.setDeployUrl(target.getDeployUrl());
			}
		}

		rollbackDeployment.setLogs("Instant rollback to deployment " + targetDeploymentId
				+ (target.getCommitSha() != null ? " (commit " + target.getCommitSha() + ")" : ""));

		deploymentRepository.save(rollbackDeployment);
		log.info("Rolled back project {} to deployment {} -> new deployment {}",
				projectId, targetDeploymentId, newDeploymentId);

		return DeploymentResponse.from(rollbackDeployment);
	}

	// ── Async pipeline ────────────────────────────────────────────────────────────

	@Async
	void triggerAsync(Deployment deployment, Project project) {
		String deploymentId = deployment.getDeploymentId();
		log.info("Starting build for deployment {}", deploymentId);

		// Mark as building
		deployment.setStatus(DeploymentStatus.BUILDING.value());
		deploymentRepository.save(deployment);

		Path workDir = null;
		try {
			workDir = Files.createTempDirectory("mv-build-" + deploymentId);

			BuildRequest request = new BuildRequest(
					project.getGitRepoUrl(),
					project.getBuildCommand(),
					project.getOutputDir(),
					deployment.getBranch(),
					deployment.getCommitSha());

			BuildResult result = buildRunner.run(request, workDir);

			if (result.success()) {
				String deployUrl = publishAndBuildUrl(result, project.getProjectId(), deploymentId);
				deployment.setDeployUrl(deployUrl);
				deployment.setStatus(DeploymentStatus.SUCCESS.value());

				// Phase 6/7: deploy any serverless function found in the repo. Previews get a
				// branch-scoped name so they never overwrite the production function.
				StringBuilder logs = new StringBuilder(result.logs() == null ? "" : result.logs());
				String functionName = functionNameFor(project.getProjectId(), deployment);
				FunctionDeployResult fn =
						functionDeployer.deploy(result.repoDir(), functionName, logs);
				if (fn.deployed()) {
					deployment.setFunctionUrl(fn.functionUrl());
					deployment.setLambdaFunctionName(fn.functionName());
				}
				else if (fn.failed()) {
					// Static site published, but the user's backend code failed to ship.
					deployment.setStatus(DeploymentStatus.FAILED.value());
					deployment.setErrorMessage(fn.message());
				}
				deployment.setLogs(truncateLogs(logs.toString()));
			}
			else {
				deployment.setStatus(DeploymentStatus.FAILED.value());
				deployment.setErrorMessage(result.errorMessage());
				deployment.setLogs(truncateLogs(result.logs()));
			}
		}
		catch (Exception ex) {
			log.error("Unexpected error during deployment {}", deploymentId, ex);
			deployment.setStatus(DeploymentStatus.FAILED.value());
			deployment.setErrorMessage("Internal error: " + ex.getMessage());
		}
		finally {
			deployment.setFinishedAt(Instant.now().toString());
			deploymentRepository.save(deployment);
			deleteQuietly(workDir);
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────────

	/**
	 * Uploads artifacts and returns the URL the end-user can visit.
	 *
	 * <p>In Phase 2 (local mode) we return a file-system path so the pipeline can be
	 * verified end-to-end without real AWS credentials.  Phase 3 swaps this for a
	 * CloudFront URL without changing anything else.
	 */
	private String publishAndBuildUrl(BuildResult result, String projectId, String deploymentId) {
		String artifactBucket = (props.aws() != null) ? props.aws().artifactBucket() : null;

		if (artifactBucket != null && !artifactBucket.isBlank()) {
			try {
				s3Publisher.publish(result.artifactDir(), projectId, deploymentId);
				String base = props.aws().publicBaseUrl();
				if (base != null && !base.isBlank()) {
					return base.stripTrailing() + "/" + projectId + "/" + deploymentId + "/index.html";
				}
				return "s3://" + artifactBucket + "/" + projectId + "/" + deploymentId + "/";
			}
			catch (Exception ex) {
				log.warn("S3 upload failed, falling back to local path", ex);
			}
		}

		// Local mode: copy artifacts to the configured local dir
		String localDir = (props.build() != null) ? props.build().localArtifactDir() : null;
		if (localDir != null && !localDir.isBlank()) {
			Path dest = Path.of(localDir, projectId, deploymentId);
			try {
				copyDirectory(result.artifactDir(), dest);
				return "file://" + dest.toAbsolutePath();
			}
			catch (Exception ex) {
				log.warn("Local artifact copy failed", ex);
			}
		}

		return "file://" + result.artifactDir().toAbsolutePath();
	}

	private Deployment requireOwned(String deploymentId, String userId) {
		return deploymentRepository.findById(deploymentId)
				.filter(d -> d.getUserId().equals(userId))
				.orElseThrow(() -> new NotFoundException("No deployment with id " + deploymentId));
	}

	/** Caps logs stored in DynamoDB at 32 KB to stay under the item size limit. */
	private static String truncateLogs(String logs) {
		if (logs == null) return null;
		int max = 32 * 1024;
		return (logs.length() <= max) ? logs : "...(truncated)\n" + logs.substring(logs.length() - max);
	}

	private static void copyDirectory(Path src, Path dest) throws java.io.IOException {
		Files.createDirectories(dest);
		try (var stream = Files.walk(src)) {
			stream.forEach(source -> {
				try {
					Files.copy(source, dest.resolve(src.relativize(source)),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				catch (java.io.IOException ex) {
					throw new RuntimeException(ex);
				}
			});
		}
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null) return;
		try (var stream = Files.walk(dir)) {
			stream.sorted(java.util.Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(java.io.File::delete);
		}
		catch (Exception ex) {
			log.warn("Could not clean up work dir {}", dir, ex);
		}
	}

	// ── Phase 7: environment classification & naming ────────────────────────────────

	/**
	 * Classifies a push into {@code production} or {@code preview}. A null/blank branch (manual
	 * trigger, or a webhook that couldn't parse a ref) is treated as production, preserving the
	 * pre-Phase-7 behavior where every deploy was effectively production.
	 */
	static String classify(String branch, String productionBranch) {
		if (branch == null || branch.isBlank()) {
			return ENV_PRODUCTION;
		}
		String prod = (productionBranch == null || productionBranch.isBlank())
				? DEFAULT_PRODUCTION_BRANCH
				: productionBranch;
		return branch.equals(prod) ? ENV_PRODUCTION : ENV_PREVIEW;
	}

	/** Production keeps the stable {@code mv-<projectId>} name; previews are branch-scoped. */
	private static String functionNameFor(String projectId, Deployment deployment) {
		if (ENV_PREVIEW.equals(deployment.getEnvironment())) {
			return previewFunctionName(projectId, deployment.getBranch());
		}
		return "mv-" + projectId;
	}

	static String previewFunctionName(String projectId, String branch) {
		return "mv-" + projectId + "-" + slugifyBranch(branch);
	}

	/** Lowercases a branch name and reduces it to {@code [a-z0-9-]} so it's URL/Lambda-safe. */
	static String slugifyBranch(String branch) {
		if (branch == null) return "";
		String slug = branch.toLowerCase()
				.replaceAll("[^a-z0-9-]", "-")
				.replaceAll("-+", "-")
				.replaceAll("^-|-$", "");
		return slug.length() > 40 ? slug.substring(0, 40) : slug;
	}
}
