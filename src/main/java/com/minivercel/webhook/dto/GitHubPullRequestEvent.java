package com.minivercel.webhook.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parsed GitHub {@code pull_request} webhook payload.
 *
 * <p>Used for preview teardown: when a PR is closed (merged or not), the preview environment
 * for its head branch is removed.
 */
public record GitHubPullRequestEvent(String repoUrl, String action, String headBranch, boolean merged) {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static GitHubPullRequestEvent parse(String json) {
		try {
			JsonNode root = MAPPER.readTree(json);

			String repoUrl = null;
			if (root.has("repository")) {
				JsonNode repo = root.get("repository");
				if (repo.has("clone_url")) {
					repoUrl = repo.get("clone_url").asText();
				}
				else if (repo.has("html_url")) {
					repoUrl = repo.get("html_url").asText();
				}
			}

			String action = root.hasNonNull("action") ? root.get("action").asText() : null;

			String headBranch = null;
			boolean merged = false;
			if (root.has("pull_request")) {
				JsonNode pr = root.get("pull_request");
				if (pr.has("head") && pr.get("head").hasNonNull("ref")) {
					headBranch = pr.get("head").get("ref").asText();
				}
				merged = pr.has("merged") && pr.get("merged").asBoolean(false);
			}

			if (repoUrl == null) {
				throw new IllegalArgumentException("Missing repository in pull_request event");
			}

			return new GitHubPullRequestEvent(repoUrl, action, headBranch, merged);
		}
		catch (Exception ex) {
			throw new RuntimeException("Failed to parse GitHub pull_request event: " + ex.getMessage(), ex);
		}
	}

	/** True when this event means the PR is finished and its preview should be torn down. */
	public boolean isClosed() {
		return "closed".equals(action);
	}
}
