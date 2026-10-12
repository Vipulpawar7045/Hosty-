package com.minivercel.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.minivercel.common.NotFoundException;
import com.minivercel.project.dto.CreateProjectRequest;
import com.minivercel.project.dto.ProjectResponse;
import com.minivercel.project.dto.UpdateProjectRequest;
import com.minivercel.quota.QuotaService;

import org.springframework.stereotype.Service;

@Service
public class ProjectService {

	private final ProjectRepository repository;
	private final QuotaService quotaService;

	public ProjectService(ProjectRepository repository, QuotaService quotaService) {
		this.repository = repository;
		this.quotaService = quotaService;
	}

	public List<ProjectResponse> listForUser(String userId) {
		return this.repository.findByUserId(userId).stream().map(ProjectResponse::from).toList();
	}

	public ProjectResponse get(String projectId, String userId) {
		return ProjectResponse.from(requireOwned(projectId, userId));
	}

	public ProjectResponse create(CreateProjectRequest request, String userId) {
		// Check project quota before creating
		quotaService.checkProjectQuota(userId);

		var project = new Project();
		project.setProjectId(UUID.randomUUID().toString());
		project.setUserId(userId);
		project.setName(request.name().trim());
		project.setGitRepoUrl(request.gitRepoUrl().trim());
		project.setBuildCommand(request.buildCommand().trim());
		project.setOutputDir(request.outputDir().trim());
		project.setProductionBranch(blankToNull(request.productionBranch()));
		project.setCreatedAt(Instant.now().toString());

		this.repository.save(project);
		return ProjectResponse.from(project);
	}

	public ProjectResponse update(String projectId, UpdateProjectRequest request, String userId) {
		var project = requireOwned(projectId, userId);
		project.setName(request.name().trim());
		project.setBuildCommand(request.buildCommand().trim());
		project.setOutputDir(request.outputDir().trim());
		project.setProductionBranch(blankToNull(request.productionBranch()));

		this.repository.save(project);
		return ProjectResponse.from(project);
	}

	public void delete(String projectId, String userId) {
		requireOwned(projectId, userId);
		this.repository.deleteById(projectId);
	}

	/**
	 * Loads a project and asserts the caller owns it.
	 *
	 * <p>Someone else's project reports as <em>not found</em> rather than forbidden, so the API
	 * never confirms which project IDs exist.
	 */
	public Project requireOwned(String projectId, String userId) {
		return this.repository.findById(projectId)
				.filter((project) -> project.getUserId().equals(userId))
				.orElseThrow(() -> new NotFoundException("No project with id " + projectId));
	}

	/** Trims, treating a null/blank value as unset so callers see {@code null} rather than "". */
	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}
}
