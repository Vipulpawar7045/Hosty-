package com.minivercel.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.minivercel.build.BuildRunner;
import com.minivercel.build.S3Publisher;
import com.minivercel.common.BadRequestException;
import com.minivercel.config.AppProperties;
import com.minivercel.deployment.dto.DeploymentResponse;
import com.minivercel.function.FunctionDeployer;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectService;
import com.minivercel.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeploymentRollbackTest {

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
	void successfulRollbackCopiesS3AndCreatesNewSuccessDeployment() {
		Deployment target = new Deployment();
		target.setDeploymentId("d-target");
		target.setProjectId(PROJECT_ID);
		target.setUserId(USER_ID);
		target.setBranch("main");
		target.setEnvironment("production");
		target.setCommitSha("abcdef123");
		target.setStatus(DeploymentStatus.SUCCESS.value());
		target.setDeployUrl("http://s3-bucket/proj1/d-target/index.html");
		target.setLambdaFunctionName("mv-proj1");
		target.setFunctionUrl("https://lambda-url.aws/test");

		when(deploymentRepository.findById("d-target")).thenReturn(Optional.of(target));

		DeploymentResponse result = service.rollback(PROJECT_ID, "d-target", USER_ID);

		assertThat(result).isNotNull();
		assertThat(result.status()).isEqualTo("success");
		assertThat(result.branch()).isEqualTo("main");
		assertThat(result.environment()).isEqualTo("production");
		assertThat(result.commitSha()).isEqualTo("abcdef123");
		assertThat(result.functionUrl()).isEqualTo("https://lambda-url.aws/test");
		assertThat(result.deployUrl()).contains("/proj1/" + result.deploymentId() + "/index.html");

		// Verify S3 copy was invoked from target prefix to new prefix
		verify(s3Publisher).copyPrefix(eq("proj1/d-target"), eq("proj1/" + result.deploymentId()));

		// Verify save was called for the new deployment
		ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
		verify(deploymentRepository).save(captor.capture());
		Deployment saved = captor.getValue();
		assertThat(saved.getDeploymentId()).isEqualTo(result.deploymentId());
		assertThat(saved.getLogs()).contains("Instant rollback to deployment d-target");
	}

	@Test
	void rollbackToFailedDeploymentThrowsBadRequest() {
		Deployment target = new Deployment();
		target.setDeploymentId("d-failed");
		target.setProjectId(PROJECT_ID);
		target.setUserId(USER_ID);
		target.setStatus(DeploymentStatus.FAILED.value());

		when(deploymentRepository.findById("d-failed")).thenReturn(Optional.of(target));

		assertThatThrownBy(() -> service.rollback(PROJECT_ID, "d-failed", USER_ID))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Cannot roll back to deployment d-failed because its status is failed");
	}

	@Test
	void rollbackToDeploymentOfDifferentProjectThrowsBadRequest() {
		Deployment target = new Deployment();
		target.setDeploymentId("d-other");
		target.setProjectId("other-proj");
		target.setUserId(USER_ID);
		target.setStatus(DeploymentStatus.SUCCESS.value());

		when(deploymentRepository.findById("d-other")).thenReturn(Optional.of(target));

		assertThatThrownBy(() -> service.rollback(PROJECT_ID, "d-other", USER_ID))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("does not belong to project proj1");
	}

	private static AppProperties props() {
		return new AppProperties(
				new AppProperties.Jwt("test-secret-test-secret-test-secret-test-secret", Duration.ofHours(1)),
				new AppProperties.Aws("ap-south-1", null, "mv-", "test-bucket", "https://cdn.example.com",
						new AppProperties.Lambda("arn:role", "nodejs20.x", "index.handler", 30, 256, "NONE"),
						new AppProperties.Domains("us-east-1", "", "")),
				new AppProperties.Build("./artifacts", Duration.ofMinutes(10), 104_857_600L,
						List.of("github.com"), List.of("npm run build")),
				new AppProperties.Webhook("secret"),
				new AppProperties.Limits(10, 10, 3));
	}
}
