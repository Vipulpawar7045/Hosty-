package com.minivercel.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.minivercel.build.BuildRunner;
import com.minivercel.build.S3Publisher;
import com.minivercel.config.AppProperties;
import com.minivercel.function.FunctionDeployer;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectService;
import com.minivercel.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for preview teardown ({@link DeploymentService#removePreview}) with mocked
 * collaborators — verifies isolation (production/other branches untouched) and idempotency.
 */
class DeploymentServiceTeardownTest {

	private static final String PROJECT_ID = "proj1";
	private static final String USER_ID = "user1";

	DeploymentRepository deploymentRepository;
	ProjectService projectService;
	S3Publisher s3Publisher;
	FunctionDeployer functionDeployer;
	DeploymentService service;

	@BeforeEach
	void setUp() {
		deploymentRepository = mock(DeploymentRepository.class);
		projectService = mock(ProjectService.class);
		s3Publisher = mock(S3Publisher.class);
		functionDeployer = mock(FunctionDeployer.class);
		BuildRunner buildRunner = mock(BuildRunner.class);
		RateLimitService rateLimitService = mock(RateLimitService.class);

		Project project = new Project();
		project.setProjectId(PROJECT_ID);
		project.setUserId(USER_ID);
		when(projectService.requireOwned(PROJECT_ID, USER_ID)).thenReturn(project);

		service = new DeploymentService(deploymentRepository, projectService, buildRunner,
				s3Publisher, functionDeployer, props(), rateLimitService);
	}

	@Test
	void removesOnlyMatchingPreviewDeployments() {
		Deployment previewA1 = deployment("d1", "preview", "feature/x");
		Deployment previewA2 = deployment("d2", "preview", "feature/x");
		Deployment otherBranch = deployment("d3", "preview", "feature/y");
		Deployment production = deployment("d4", "production", "main");
		when(deploymentRepository.findByProjectId(eq(PROJECT_ID), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(List.of(previewA1, previewA2, otherBranch, production));

		int removed = service.removePreview(PROJECT_ID, USER_ID, "feature/x");

		assertThat(removed).isEqualTo(2);

		// The branch-scoped Lambda is deleted (never the production mv-proj1).
		verify(functionDeployer).remove("mv-proj1-feature-x");

		// Only the two matching previews get their S3 artifacts deleted and are marked removed.
		verify(s3Publisher).deletePrefix("proj1/d1");
		verify(s3Publisher).deletePrefix("proj1/d2");
		verify(s3Publisher, never()).deletePrefix("proj1/d3");
		verify(s3Publisher, never()).deletePrefix("proj1/d4");

		assertThat(previewA1.getStatus()).isEqualTo("removed");
		assertThat(previewA2.getStatus()).isEqualTo("removed");
		assertThat(otherBranch.getStatus()).isEqualTo("preview");
		assertThat(production.getStatus()).isEqualTo("production-status-untouched");
	}

	@Test
	void alreadyRemovedDeploymentsAreSkipped() {
		Deployment already = deployment("d1", "preview", "feature/x");
		already.setStatus("removed");
		when(deploymentRepository.findByProjectId(eq(PROJECT_ID), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(List.of(already));

		int removed = service.removePreview(PROJECT_ID, USER_ID, "feature/x");

		assertThat(removed).isZero();
		verify(s3Publisher, never()).deletePrefix(any());
		// The function delete is still attempted (idempotent, safe if already gone).
		verify(functionDeployer).remove("mv-proj1-feature-x");
	}

	@Test
	void blankBranchIsNoOp() {
		int removed = service.removePreview(PROJECT_ID, USER_ID, "  ");

		assertThat(removed).isZero();
		verify(functionDeployer, never()).remove(any());
		verify(deploymentRepository, never()).findByProjectId(any(), org.mockito.ArgumentMatchers.anyInt());
	}

	// ── Fixtures ───────────────────────────────────────────────────────────────────────

	private static Deployment deployment(String id, String environment, String branch) {
		Deployment d = new Deployment();
		d.setDeploymentId(id);
		d.setProjectId(PROJECT_ID);
		d.setUserId(USER_ID);
		d.setEnvironment(environment);
		d.setBranch(branch);
		// Give production a sentinel status so we can assert it's never mutated.
		d.setStatus("production".equals(environment) ? "production-status-untouched" : environment);
		return d;
	}

	private static AppProperties props() {
		return new AppProperties(
				new AppProperties.Jwt("test-secret-test-secret-test-secret-test-secret", Duration.ofHours(1)),
				new AppProperties.Aws("ap-south-1", null, "mv-", "bucket", "https://base.example.com",
						new AppProperties.Lambda("arn:role", "nodejs20.x", "index.handler", 30, 256, "NONE"),
						new AppProperties.Domains("us-east-1", "", "")),
				new AppProperties.Build("./artifacts", Duration.ofMinutes(10), 104_857_600L,
						List.of("github.com"), List.of("npm run build")),
				new AppProperties.Webhook("secret"),
				new AppProperties.Limits(10, 10, 3));
	}
}
