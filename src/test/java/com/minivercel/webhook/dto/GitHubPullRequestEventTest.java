package com.minivercel.webhook.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubPullRequestEventTest {

	@Test
	void parseClosedPullRequestEvent() {
		String json = """
				{
				  "action": "closed",
				  "repository": {
				    "clone_url": "https://github.com/user/test-site.git"
				  },
				  "pull_request": {
				    "head": {
				      "ref": "feature/test-branch"
				    },
				    "merged": true
				  }
				}
				""";

		GitHubPullRequestEvent event = GitHubPullRequestEvent.parse(json);

		assertThat(event.repoUrl()).isEqualTo("https://github.com/user/test-site.git");
		assertThat(event.action()).isEqualTo("closed");
		assertThat(event.headBranch()).isEqualTo("feature/test-branch");
		assertThat(event.merged()).isTrue();
		assertThat(event.isClosed()).isTrue();
	}

	@Test
	void parseOpenPullRequestEvent() {
		String json = """
				{
				  "action": "opened",
				  "repository": {
				    "html_url": "https://github.com/user/test-site"
				  },
				  "pull_request": {
				    "head": {
				      "ref": "feature/new-feature"
				    },
				    "merged": false
				  }
				}
				""";

		GitHubPullRequestEvent event = GitHubPullRequestEvent.parse(json);

		assertThat(event.action()).isEqualTo("opened");
		assertThat(event.isClosed()).isFalse();
		assertThat(event.headBranch()).isEqualTo("feature/new-feature");
	}

	@Test
	void parseThrowsOnMissingRepository() {
		String json = """
				{
				  "action": "closed"
				}
				""";

		assertThatThrownBy(() -> GitHubPullRequestEvent.parse(json))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Missing repository in pull_request event");
	}
}
