package com.minivercel.webhook.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for parsing GitHub push payloads, focused on the Phase 7 additions:
 * branch extraction from {@code ref} and branch-deletion detection.
 */
class GitHubPushEventTest {

	@Test
	void parsesRepoCommitAndBranchFromNormalPush() {
		String json = """
			{
			  "ref": "refs/heads/feature/login",
			  "deleted": false,
			  "after": "abc123",
			  "head_commit": { "id": "abc123" },
			  "repository": { "clone_url": "https://github.com/acme/site.git" }
			}
			""";

		GitHubPushEvent event = GitHubPushEvent.parse(json);

		assertThat(event.repoUrl()).isEqualTo("https://github.com/acme/site.git");
		assertThat(event.commitSha()).isEqualTo("abc123");
		assertThat(event.branch()).isEqualTo("feature/login");
		assertThat(event.deleted()).isFalse();
	}

	@Test
	void detectsBranchDeletionWithZeroedSha() {
		// A branch-delete push: deleted=true, no head_commit, "after" is all zeros.
		String json = """
			{
			  "ref": "refs/heads/feature/login",
			  "deleted": true,
			  "after": "0000000000000000000000000000000000000000",
			  "head_commit": null,
			  "repository": { "clone_url": "https://github.com/acme/site.git" }
			}
			""";

		GitHubPushEvent event = GitHubPushEvent.parse(json);

		assertThat(event.deleted()).isTrue();
		assertThat(event.branch()).isEqualTo("feature/login");
		// Zeroed SHA is treated as absent, so the webhook won't try to deploy it.
		assertThat(event.commitSha()).isNull();
	}

	@Test
	void tagPushHasNullBranch() {
		String json = """
			{
			  "ref": "refs/tags/v1.0.0",
			  "deleted": false,
			  "after": "abc123",
			  "head_commit": { "id": "abc123" },
			  "repository": { "clone_url": "https://github.com/acme/site.git" }
			}
			""";

		GitHubPushEvent event = GitHubPushEvent.parse(json);

		assertThat(event.branch()).isNull();
		assertThat(event.commitSha()).isEqualTo("abc123");
	}

	@Test
	void fallsBackToHtmlUrlWhenNoCloneUrl() {
		String json = """
			{
			  "ref": "refs/heads/main",
			  "after": "abc123",
			  "head_commit": { "id": "abc123" },
			  "repository": { "html_url": "https://github.com/acme/site" }
			}
			""";

		GitHubPushEvent event = GitHubPushEvent.parse(json);

		assertThat(event.repoUrl()).isEqualTo("https://github.com/acme/site");
		assertThat(event.branch()).isEqualTo("main");
	}
}
