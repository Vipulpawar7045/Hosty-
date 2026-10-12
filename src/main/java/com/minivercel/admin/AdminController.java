package com.minivercel.admin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.minivercel.deployment.Deployment;
import com.minivercel.deployment.DeploymentRepository;
import com.minivercel.deployment.DeploymentStatus;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectRepository;
import com.minivercel.user.User;
import com.minivercel.user.UserRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for monitoring system health and usage.
 *
 * NOTE: Currently unprotected — add admin role check in production.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final DeploymentRepository deploymentRepository;

	public AdminController(UserRepository userRepository,
			ProjectRepository projectRepository,
			DeploymentRepository deploymentRepository) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.deploymentRepository = deploymentRepository;
	}

	@GetMapping("/stats")
	public Map<String, Object> stats() {
		List<User> allUsers = userRepository.findAll();
		List<Project> allProjects = projectRepository.findAll();

		long totalDeployments = allProjects.stream()
				.mapToLong(p -> deploymentRepository.findByProjectId(p.getProjectId(), 1000).size())
				.sum();

		Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
		long recentDeployments = allProjects.stream()
				.flatMap(p -> deploymentRepository.findByProjectId(p.getProjectId(), 100).stream())
				.filter(d -> {
					try {
						return Instant.parse(d.getCreatedAt()).isAfter(oneHourAgo);
					}
					catch (Exception ex) {
						return false;
					}
				})
				.count();

		long activeDeployments = allProjects.stream()
				.flatMap(p -> deploymentRepository.findByProjectId(p.getProjectId(), 100).stream())
				.filter(d -> DeploymentStatus.QUEUED.value().equals(d.getStatus())
						|| DeploymentStatus.BUILDING.value().equals(d.getStatus()))
				.count();

		return Map.of(
				"totalUsers", allUsers.size(),
				"totalProjects", allProjects.size(),
				"totalDeployments", totalDeployments,
				"deploymentsLastHour", recentDeployments,
				"activeDeployments", activeDeployments
		);
	}

	@GetMapping("/active-deployments")
	public List<DeploymentSummary> activeDeployments() {
		List<Project> allProjects = projectRepository.findAll();

		return allProjects.stream()
				.flatMap(p -> deploymentRepository.findByProjectId(p.getProjectId(), 100).stream()
						.filter(d -> DeploymentStatus.QUEUED.value().equals(d.getStatus())
								|| DeploymentStatus.BUILDING.value().equals(d.getStatus()))
						.map(d -> new DeploymentSummary(
								d.getDeploymentId(),
								p.getProjectId(),
								p.getName(),
								d.getStatus(),
								d.getCreatedAt())))
				.toList();
	}

	public record DeploymentSummary(
			String deploymentId,
			String projectId,
			String projectName,
			String status,
			String createdAt) {
	}
}
