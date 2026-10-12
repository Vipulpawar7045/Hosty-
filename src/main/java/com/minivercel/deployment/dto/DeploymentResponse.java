package com.minivercel.deployment.dto;

import com.minivercel.deployment.Deployment;

/**
 * What the API returns for a deployment — never exposes raw DynamoDB beans.
 */
public record DeploymentResponse(
		String deploymentId,
		String projectId,
		String status,
		String deployUrl,
		String functionUrl,
		String branch,
		String environment,
		String commitSha,
		String logs,
		String errorMessage,
		String createdAt,
		String finishedAt) {

	public static DeploymentResponse from(Deployment d) {
		return new DeploymentResponse(
				d.getDeploymentId(),
				d.getProjectId(),
				d.getStatus(),
				d.getDeployUrl(),
				d.getFunctionUrl(),
				d.getBranch(),
				d.getEnvironment(),
				d.getCommitSha(),
				d.getLogs(),
				d.getErrorMessage(),
				d.getCreatedAt(),
				d.getFinishedAt());
	}
}
