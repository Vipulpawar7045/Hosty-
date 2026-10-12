package com.minivercel.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Same shape as create; the repo URL is intentionally immutable once a project exists. */
public record UpdateProjectRequest(
		@NotBlank @Size(max = 60)
		@Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9 _-]*$",
				message = "may contain only letters, digits, spaces, hyphens and underscores")
		String name,

		@NotBlank @Size(max = 200) String buildCommand,

		@NotBlank @Size(max = 100)
		@Pattern(regexp = "^[a-zA-Z0-9._/-]+$", message = "must be a relative path")
		String outputDir,

		// Phase 7: branch whose pushes deploy to production; every other branch becomes a
		// preview. Optional — when blank the platform falls back to "main".
		@Size(max = 100)
		@Pattern(regexp = "^[\\w./-]*$", message = "must be a valid git branch name")
		String productionBranch) {
}
