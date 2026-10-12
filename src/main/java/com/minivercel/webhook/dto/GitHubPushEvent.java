package com.minivercel.webhook.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parsed GitHub push webhook payload.
 *
 * <p>Carries everything Phase 4/7 need: repo URL, commit SHA, the ref that was pushed,
 * and whether this is a branch-deletion push (which triggers preview teardown).
 */
public record GitHubPushEvent(String repoUrl, String commitSha, String ref, boolean deleted) {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Parses a GitHub push event payload.
	 *
	 * @param json the raw JSON payload
	 * @return the parsed event
	 * @throws RuntimeException if parsing fails
	 */
	public static GitHubPushEvent parse(String json) {
		try {
			JsonNode root = MAPPER.readTree(json);

			// Extract repo URL from repository.clone_url or repository.html_url
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

			// Extract commit SHA from head_commit.id or after (refs/heads/main).
			// A branch-deletion push has no head commit — "after" is all zeros, which we treat as absent.
			String commitSha = null;
			if (root.has("head_commit") && root.get("head_commit").has("id")) {
				commitSha = root.get("head_commit").get("id").asText();
			}
			else if (root.has("after")) {
				String after = root.get("after").asText();
				if (!after.matches("0+")) {
					commitSha = after;
				}
			}

			String ref = root.hasNonNull("ref") ? root.get("ref").asText() : null;
			boolean deleted = root.has("deleted") && root.get("deleted").asBoolean(false);

			if (repoUrl == null) {
				throw new IllegalArgumentException("Missing required fields in GitHub push event");
			}

			return new GitHubPushEvent(repoUrl, commitSha, ref, deleted);
		}
		catch (Exception ex) {
			throw new RuntimeException("Failed to parse GitHub push event: " + ex.getMessage(), ex);
		}
	}

	/**
	 * The branch name for pushes to {@code refs/heads/*}; null for tag pushes or refs we don't handle.
	 */
	public String branch() {
		if (ref == null || !ref.startsWith("refs/heads/")) {
			return null;
		}
		return ref.substring("refs/heads/".length());
	}
}
