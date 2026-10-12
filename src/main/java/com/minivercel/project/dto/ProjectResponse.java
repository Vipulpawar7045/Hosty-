package com.minivercel.project.dto;

import com.minivercel.project.Project;

public record ProjectResponse(String projectId, String name, String gitRepoUrl, String buildCommand,
		String outputDir, String createdAt, String productionBranch, String functionDir,
		String functionRuntime, String functionHandler) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(project.getProjectId(), project.getName(),
				project.getGitRepoUrl(), project.getBuildCommand(), project.getOutputDir(),
				project.getCreatedAt(), project.getProductionBranch(), project.getFunctionDir(),
				project.getFunctionRuntime(), project.getFunctionHandler());
	}
}
