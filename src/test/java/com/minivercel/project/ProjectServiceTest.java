package com.minivercel.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.minivercel.project.dto.CreateProjectRequest;
import com.minivercel.project.dto.ProjectResponse;
import com.minivercel.project.dto.UpdateProjectRequest;
import com.minivercel.quota.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies that a project's {@code productionBranch} is actually persisted through create/update
 * and surfaced on the response. This is what {@link com.minivercel.deployment.DeploymentService}
 * keys on to classify a push as production vs. preview (Phase 7); before it was wired, the field
 * was always null and every repo silently fell back to the hardcoded "main".
 */
class ProjectServiceTest {

	private static final String USER_ID = "user1";

	ProjectRepository repository;
	QuotaService quotaService;
	ProjectService service;

	@BeforeEach
	void setUp() {
		repository = mock(ProjectRepository.class);
		quotaService = mock(QuotaService.class);
		service = new ProjectService(repository, quotaService);
	}

	@Test
	void createPersistsProductionBranch() {
		CreateProjectRequest request = createRequest("master");

		ProjectResponse response = service.create(request, USER_ID);

		assertThat(response.productionBranch()).isEqualTo("master");
		assertThat(savedProject().getProductionBranch()).isEqualTo("master");
	}

	@Test
	void createLeavesProductionBranchNullWhenBlank() {
		// Blank/omitted must stay null so DeploymentService.classify() applies its "main" default,
		// rather than storing "" and treating an empty branch as production.
		ProjectResponse response = service.create(createRequest("  "), USER_ID);

		assertThat(response.productionBranch()).isNull();
		assertThat(savedProject().getProductionBranch()).isNull();
	}

	@Test
	void updateChangesProductionBranch() {
		Project existing = new Project();
		existing.setProjectId("p1");
		existing.setUserId(USER_ID);
		existing.setProductionBranch("main");
		when(repository.findById("p1")).thenReturn(Optional.of(existing));

		ProjectResponse response = service.update("p1",
				new UpdateProjectRequest("site", "npm run build", "dist", "release/prod"), USER_ID);

		assertThat(response.productionBranch()).isEqualTo("release/prod");
		assertThat(existing.getProductionBranch()).isEqualTo("release/prod");
	}

	// ── Helpers ──────────────────────────────────────────────────────────────────────

	private Project savedProject() {
		ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}

	private static CreateProjectRequest createRequest(String productionBranch) {
		return new CreateProjectRequest("site", "https://github.com/user/repo", "npm run build",
				"dist", null, null, null, productionBranch);
	}
}
