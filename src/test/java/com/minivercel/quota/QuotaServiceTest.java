package com.minivercel.quota;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.minivercel.common.RateLimitException;
import com.minivercel.config.AppProperties;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaServiceTest {

	private ProjectRepository projectRepository;
	private AppProperties props;
	private QuotaService quotaService;

	@BeforeEach
	void setUp() {
		projectRepository = mock(ProjectRepository.class);
		props = new AppProperties(
				null, null, null, null,
				new AppProperties.Limits(3, 10, 3));
		quotaService = new QuotaService(projectRepository, props);
	}

	@Test
	void checkProjectQuotaAllowsUnderLimit() {
		List<Project> existing = List.of(new Project(), new Project());
		when(projectRepository.findByUserId("user-1")).thenReturn(existing);

		assertThatCode(() -> quotaService.checkProjectQuota("user-1"))
				.doesNotThrowAnyException();
	}

	@Test
	void checkProjectQuotaThrowsWhenLimitReached() {
		List<Project> existing = List.of(new Project(), new Project(), new Project());
		when(projectRepository.findByUserId("user-1")).thenReturn(existing);

		assertThatThrownBy(() -> quotaService.checkProjectQuota("user-1"))
				.isInstanceOf(RateLimitException.class)
				.hasMessageContaining("Project limit reached (3/3)");
	}
}
