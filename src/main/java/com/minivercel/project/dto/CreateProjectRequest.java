package com.minivercel.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
		@NotBlank @Size(max = 60)
		@Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9 _-]*$",
				message = "may contain only letters, digits, spaces, hyphens and underscores")
		String name,

		@NotBlank
		@Pattern(regexp = "^https://[\\w.-]+/[\\w./-]+$",
				message = "must be an https Git URL, e.g. https://github.com/user/repo")
		String gitRepoUrl,

		@NotBlank @Size(max = 200) String buildCommand,

		@NotBlank @Size(max = 100)
		@Pattern(regexp = "^[a-zA-Z0-9._/-]+$", message = "must be a relative path")
		String outputDir,

		// Phase 6: Optional serverless function configuration
		@Size(max = 100)
		@Pattern(regexp = "^[a-zA-Z0-9._/-]*$", message = "must be a relative path")
		String functionDir,

		@Size(max = 50)
		@Pattern(regexp = "^(nodejs20\\.x|python3\\.12|java21)?$", message = "must be a valid runtime")
		String functionRuntime,

		@Size(max = 100)
		String functionHandler,

		// Phase 7: branch whose pushes deploy to production; every other branch becomes a
		// preview. Optional — when blank the platform falls back to "main".
		@Size(max = 100)
		@Pattern(regexp = "^[\\w./-]*$", message = "must be a valid git branch name")
		String productionBranch) {
}
