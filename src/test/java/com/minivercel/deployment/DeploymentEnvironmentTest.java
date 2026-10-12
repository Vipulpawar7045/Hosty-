package com.minivercel.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the branch → environment classification and Lambda-name slugging
 * introduced in Phase 7. All targets are package-private statics on {@link DeploymentService}.
 */
class DeploymentEnvironmentTest {

	// ── classify ─────────────────────────────────────────────────────────────────────

	@Test
	void productionBranchClassifiesAsProduction() {
		assertThat(DeploymentService.classify("main", "main")).isEqualTo("production");
		assertThat(DeploymentService.classify("release", "release")).isEqualTo("production");
	}

	@Test
	void otherBranchesClassifyAsPreview() {
		assertThat(DeploymentService.classify("feature/login", "main")).isEqualTo("preview");
		assertThat(DeploymentService.classify("dependabot/npm/lodash", "main")).isEqualTo("preview");
	}

	@Test
	void nullOrBlankBranchDefaultsToProduction() {
		// Manual triggers pass no branch — preserve pre-Phase-7 behavior (always production).
		assertThat(DeploymentService.classify(null, "main")).isEqualTo("production");
		assertThat(DeploymentService.classify("  ", "main")).isEqualTo("production");
	}

	@Test
	void nullProductionBranchFallsBackToMain() {
		// A project with no configured production branch treats "main" as production.
		assertThat(DeploymentService.classify("main", null)).isEqualTo("production");
		assertThat(DeploymentService.classify("dev", null)).isEqualTo("preview");
	}

	// ── slugifyBranch ─────────────────────────────────────────────────────────────────

	@Test
	void slugifyLowercasesAndReplacesUnsafeChars() {
		assertThat(DeploymentService.slugifyBranch("Feature/Login")).isEqualTo("feature-login");
		assertThat(DeploymentService.slugifyBranch("JIRA-123_fix")).isEqualTo("jira-123-fix");
	}

	@Test
	void slugifyCollapsesRunsAndTrimsDashes() {
		assertThat(DeploymentService.slugifyBranch("///weird///")).isEqualTo("weird");
		assertThat(DeploymentService.slugifyBranch("a//b")).isEqualTo("a-b");
	}

	@Test
	void slugifyCapsLengthAtForty() {
		String longBranch = "feature/" + "x".repeat(100);
		assertThat(DeploymentService.slugifyBranch(longBranch)).hasSize(40);
	}

	@Test
	void slugifyHandlesNull() {
		assertThat(DeploymentService.slugifyBranch(null)).isEmpty();
	}

	// ── previewFunctionName ─────────────────────────────────────────────────────────────

	@Test
	void previewFunctionNameIsBranchScoped() {
		assertThat(DeploymentService.previewFunctionName("proj1", "feature/login"))
				.isEqualTo("mv-proj1-feature-login");
	}
}
