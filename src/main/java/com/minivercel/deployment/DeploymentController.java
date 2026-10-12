package com.minivercel.deployment;

import java.util.List;
import java.util.Map;

import com.minivercel.common.CurrentUser;
import com.minivercel.deployment.dto.CancelDeploymentRequest;
import com.minivercel.deployment.dto.DeploymentResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/deployments")
public class DeploymentController {

	private final DeploymentService deploymentService;

	public DeploymentController(DeploymentService deploymentService) {
		this.deploymentService = deploymentService;
	}

	public record TriggerDeploymentRequest(String branch, String commitSha) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeploymentResponse trigger(@PathVariable String projectId,
			@RequestBody(required = false) TriggerDeploymentRequest request,
			@CurrentUser String userId) {
		String branch = (request != null) ? request.branch() : null;
		String commitSha = (request != null) ? request.commitSha() : null;
		return this.deploymentService.enqueue(projectId, userId, branch, commitSha);
	}

	@GetMapping
	public List<DeploymentResponse> list(@PathVariable String projectId, @CurrentUser String userId) {
		return this.deploymentService.listForProject(projectId, userId);
	}

	@GetMapping("/{deploymentId}")
	public DeploymentResponse get(@PathVariable String projectId,
			@PathVariable String deploymentId,
			@CurrentUser String userId) {
		return this.deploymentService.get(deploymentId, userId);
	}

	@PostMapping("/{deploymentId}/cancel")
	public DeploymentResponse cancel(@PathVariable String projectId,
			@PathVariable String deploymentId,
			@RequestBody @Valid CancelDeploymentRequest request,
			@CurrentUser String userId) {
		return this.deploymentService.cancel(deploymentId, userId, request.reason());
	}

	@PostMapping("/{deploymentId}/rollback")
	public DeploymentResponse rollback(@PathVariable String projectId,
			@PathVariable String deploymentId,
			@CurrentUser String userId) {
		return this.deploymentService.rollback(projectId, deploymentId, userId);
	}

	/**
	 * Manually tears down a branch's preview environment (Lambda + S3 + records). The branch is a
	 * query param rather than a path variable because branch names often contain '/'.
	 */
	@DeleteMapping("/previews")
	public Map<String, Object> removePreview(@PathVariable String projectId,
			@RequestParam String branch,
			@CurrentUser String userId) {
		int removed = this.deploymentService.removePreview(projectId, userId, branch);
		return Map.of("branch", branch, "removed", removed);
	}
}
