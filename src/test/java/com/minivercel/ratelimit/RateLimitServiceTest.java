package com.minivercel.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.minivercel.common.RateLimitException;
import com.minivercel.config.AppProperties;
import com.minivercel.deployment.Deployment;
import com.minivercel.deployment.DeploymentRepository;
import com.minivercel.deployment.DeploymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

	private DeploymentRepository deploymentRepository;
	private AppProperties props;
	private RateLimitService rateLimitService;

	@BeforeEach
	void setUp() {
		deploymentRepository = mock(DeploymentRepository.class);
		props = new AppProperties(
				null, null, null, null,
				new AppProperties.Limits(10, 5, 2));
		rateLimitService = new RateLimitService(deploymentRepository, props);
	}

	@Test
	void allowDeploymentWhenUnderLimits() {
		Deployment d1 = new Deployment();
		d1.setStatus(DeploymentStatus.SUCCESS.value());
		d1.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS).toString());

		when(deploymentRepository.findByProjectId("p1", 100)).thenReturn(List.of(d1));

		assertThatCode(() -> rateLimitService.checkDeploymentLimits("p1"))
				.doesNotThrowAnyException();
	}

	@Test
	void throwWhenActiveDeploymentLimitReached() {
		Deployment d1 = new Deployment();
		d1.setStatus(DeploymentStatus.BUILDING.value());
		d1.setCreatedAt(Instant.now().toString());

		Deployment d2 = new Deployment();
		d2.setStatus(DeploymentStatus.QUEUED.value());
		d2.setCreatedAt(Instant.now().toString());

		when(deploymentRepository.findByProjectId("p1", 100)).thenReturn(List.of(d1, d2));

		assertThatThrownBy(() -> rateLimitService.checkDeploymentLimits("p1"))
				.isInstanceOf(RateLimitException.class)
				.hasMessageContaining("Too many active deployments");
	}

	@Test
	void throwWhenHourlyDeploymentLimitReached() {
		List<Deployment> hourly = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Deployment d = new Deployment();
			d.setStatus(DeploymentStatus.SUCCESS.value());
			d.setCreatedAt(Instant.now().minus(i * 5, ChronoUnit.MINUTES).toString());
			hourly.add(d);
		}

		when(deploymentRepository.findByProjectId("p1", 100)).thenReturn(hourly);

		assertThatThrownBy(() -> rateLimitService.checkDeploymentLimits("p1"))
				.isInstanceOf(RateLimitException.class)
				.hasMessageContaining("Hourly deployment limit exceeded (5/5)");
	}
}
