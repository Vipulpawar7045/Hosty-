package com.minivercel.ratelimit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.minivercel.common.RateLimitException;
import com.minivercel.config.AppProperties;
import com.minivercel.deployment.Deployment;
import com.minivercel.deployment.DeploymentRepository;
import com.minivercel.deployment.DeploymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/**
 * Enforces rate limits and quotas for deployments and projects.
 */
@Service
public class RateLimitService {

	private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

	private final DeploymentRepository deploymentRepository;
	private final AppProperties props;

	public RateLimitService(DeploymentRepository deploymentRepository, AppProperties props) {
		this.deploymentRepository = deploymentRepository;
		this.props = props;
	}

	/**
	 * Checks if a new deployment can be created for the given project.
	 *
	 * @param projectId the project ID
	 * @throws RateLimitException if rate limits are exceeded
	 */
	public void checkDeploymentLimits(String projectId) {
		checkActiveDeploymentLimit(projectId);
		checkHourlyDeploymentLimit(projectId);
	}

	/**
	 * Checks the number of active (queued or building) deployments for a project.
	 */
	private void checkActiveDeploymentLimit(String projectId) {
		List<Deployment> recent = deploymentRepository.findByProjectId(projectId, 100);

		long activeCount = recent.stream()
				.filter(d -> DeploymentStatus.QUEUED.value().equals(d.getStatus())
						|| DeploymentStatus.BUILDING.value().equals(d.getStatus()))
				.count();

		int maxActive = props.limits().maxActiveDeployments();
		if (activeCount >= maxActive) {
			log.warn("Project {} has {} active deployments (limit: {})", projectId, activeCount, maxActive);
			throw new RateLimitException(
					"Too many active deployments. Please wait for some to complete before starting new ones.");
		}
	}

	/**
	 * Checks the number of deployments created in the last hour for a project.
	 */
	private void checkHourlyDeploymentLimit(String projectId) {
		List<Deployment> recent = deploymentRepository.findByProjectId(projectId, 100);

		Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

		long recentCount = recent.stream()
				.filter(d -> {
					try {
						Instant createdAt = Instant.parse(d.getCreatedAt());
						return createdAt.isAfter(oneHourAgo);
					}
					catch (Exception ex) {
						return false;
					}
				})
				.count();

		int maxPerHour = props.limits().maxDeploymentsPerHour();
		if (recentCount >= maxPerHour) {
			log.warn("Project {} has {} deployments in the last hour (limit: {})",
					projectId, recentCount, maxPerHour);
			throw new RateLimitException(
					String.format("Hourly deployment limit exceeded (%d/%d). Please try again later.",
							recentCount, maxPerHour));
		}
	}
}
