package com.minivercel.quota;

import java.util.List;

import com.minivercel.common.RateLimitException;
import com.minivercel.config.AppProperties;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/**
 * Enforces user-level quotas (max projects, etc).
 */
@Service
public class QuotaService {

	private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

	private final ProjectRepository projectRepository;
	private final AppProperties props;

	public QuotaService(ProjectRepository projectRepository, AppProperties props) {
		this.projectRepository = projectRepository;
		this.props = props;
	}

	/**
	 * Checks if the user has reached their project creation limit.
	 *
	 * @param userId the user ID
	 * @throws RateLimitException if the quota is exceeded
	 */
	public void checkProjectQuota(String userId) {
		List<Project> userProjects = projectRepository.findByUserId(userId);
		int maxProjects = props.limits().maxProjectsPerUser();

		if (userProjects.size() >= maxProjects) {
			log.warn("User {} has {} projects (limit: {})", userId, userProjects.size(), maxProjects);
			throw new RateLimitException(
					String.format("Project limit reached (%d/%d). Delete some projects before creating new ones.",
							userProjects.size(), maxProjects));
		}
	}
}
