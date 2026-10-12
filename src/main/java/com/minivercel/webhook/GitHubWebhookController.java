package com.minivercel.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.minivercel.config.AppProperties;
import com.minivercel.deployment.DeploymentService;
import com.minivercel.deployment.dto.DeploymentResponse;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectRepository;
import com.minivercel.webhook.dto.GitHubPullRequestEvent;
import com.minivercel.webhook.dto.GitHubPushEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives GitHub webhook push events and triggers deployments automatically.
 *
 * <p>Verifies the HMAC-SHA256 signature from {@code X-Hub-Signature-256} header
 * to ensure the request actually came from GitHub.
 */
@RestController
@RequestMapping("/api/webhooks")
public class GitHubWebhookController {

	private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

	private final ProjectRepository projectRepository;
	private final DeploymentService deploymentService;
	private final AppProperties props;

	public GitHubWebhookController(
			ProjectRepository projectRepository,
			DeploymentService deploymentService,
			AppProperties props) {
		this.projectRepository = projectRepository;
		this.deploymentService = deploymentService;
		this.props = props;
	}

	@PostMapping("/github")
	public ResponseEntity<?> handleGitHubPush(
			@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestHeader(value = "X-GitHub-Event", required = false) String event,
			@RequestBody String rawPayload) {

		// Verify signature first — reject forged requests before doing any parsing/work.
		if (!verifySignature(rawPayload, signature)) {
			log.warn("Invalid webhook signature from GitHub");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "Invalid signature"));
		}

		try {
			return switch (event == null ? "" : event) {
				case "push" -> handlePush(rawPayload);
				case "pull_request" -> handlePullRequest(rawPayload);
				default -> {
					log.debug("Ignoring unsupported event: {}", event);
					yield ResponseEntity.ok(Map.of("message", "Event ignored"));
				}
			};
		}
		catch (Exception ex) {
			log.error("Error processing GitHub webhook", ex);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to process webhook: " + ex.getMessage()));
		}
	}

	/**
	 * A push either deploys (normal push) or tears down a preview (branch-deletion push, which
	 * GitHub sends with {@code deleted:true} and a zeroed commit SHA).
	 */
	private ResponseEntity<?> handlePush(String rawPayload) {
		GitHubPushEvent pushEvent = GitHubPushEvent.parse(rawPayload);
		String branch = pushEvent.branch();

		Project project = projectRepository.findByGitRepoUrl(pushEvent.repoUrl()).orElse(null);
		if (project == null) {
			log.info("No project found for repo URL: {}", pushEvent.repoUrl());
			return ResponseEntity.ok(Map.of("message", "No project configured for this repository"));
		}

		// Branch deleted → tear down its preview environment.
		if (pushEvent.deleted()) {
			if (branch == null) {
				return ResponseEntity.ok(Map.of("message", "Non-branch delete ignored"));
			}
			int removed = deploymentService.removePreviewForWebhook(project.getProjectId(), branch);
			log.info("Branch {} deleted — removed {} preview deployment(s) for project {}",
					branch, removed, project.getProjectId());
			return ResponseEntity.ok(Map.of(
					"message", "Preview removed",
					"branch", branch,
					"removed", removed));
		}

		// A push with no head commit and no delete flag (e.g. a tag push) — nothing to deploy.
		if (pushEvent.commitSha() == null) {
			return ResponseEntity.ok(Map.of("message", "No commit to deploy"));
		}

		log.info("Received push for repo: {}, branch: {}, commit: {}",
				pushEvent.repoUrl(), branch, pushEvent.commitSha());

		DeploymentResponse deployment = deploymentService.enqueue(
				project.getProjectId(),
				project.getUserId(),
				branch,
				pushEvent.commitSha());

		log.info("Triggered {} deployment {} for project {}",
				deployment.environment(), deployment.deploymentId(), project.getProjectId());

		return ResponseEntity.ok(Map.of(
				"message", "Deployment triggered",
				"deploymentId", deployment.deploymentId(),
				"environment", String.valueOf(deployment.environment()),
				"projectId", project.getProjectId()));
	}

	/** A closed PR (merged or not) tears down the preview for its head branch. */
	private ResponseEntity<?> handlePullRequest(String rawPayload) {
		GitHubPullRequestEvent prEvent = GitHubPullRequestEvent.parse(rawPayload);

		if (!prEvent.isClosed()) {
			return ResponseEntity.ok(Map.of("message", "PR action ignored: " + prEvent.action()));
		}

		Project project = projectRepository.findByGitRepoUrl(prEvent.repoUrl()).orElse(null);
		if (project == null || prEvent.headBranch() == null) {
			return ResponseEntity.ok(Map.of("message", "No preview to remove"));
		}

		int removed = deploymentService.removePreviewForWebhook(project.getProjectId(), prEvent.headBranch());
		log.info("PR closed (merged={}) — removed {} preview deployment(s) for {}#{}",
				prEvent.merged(), removed, project.getProjectId(), prEvent.headBranch());

		return ResponseEntity.ok(Map.of(
				"message", "Preview removed",
				"branch", prEvent.headBranch(),
				"merged", prEvent.merged(),
				"removed", removed));
	}

	/**
	 * Verifies the HMAC-SHA256 signature from GitHub.
	 *
	 * @param payload the raw request body
	 * @param signatureHeader the X-Hub-Signature-256 header value (format: "sha256=...")
	 * @return true if the signature is valid
	 */
	private boolean verifySignature(String payload, String signatureHeader) {
		if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
			return false;
		}

		String secret = props.webhook().githubSecret();
		if (secret == null || secret.isBlank()) {
			log.warn("GitHub webhook secret not configured, skipping signature verification");
			return true; // Allow in dev mode when secret is not set
		}

		try {
			String expectedSignature = signatureHeader.substring(7); // Remove "sha256=" prefix
			String computedSignature = computeHmacSha256(payload, secret);
			return MessageDigest.isEqual(
					expectedSignature.getBytes(StandardCharsets.UTF_8),
					computedSignature.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			log.error("Error verifying webhook signature", ex);
			return false;
		}
	}

	private String computeHmacSha256(String data, String secret)
			throws NoSuchAlgorithmException, InvalidKeyException {
		Mac mac = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		mac.init(secretKey);
		byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		return bytesToHex(hash);
	}

	private String bytesToHex(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for (byte b : bytes) {
			result.append(String.format("%02x", b));
		}
		return result.toString();
	}
}
