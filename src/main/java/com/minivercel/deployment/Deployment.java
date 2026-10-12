package com.minivercel.deployment;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@DynamoDbBean
public class Deployment {

	/** Partitioned by project, sorted by time, so history reads newest-first without a scan. */
	public static final String PROJECT_INDEX = "projectId-createdAt-index";

	private String deploymentId;
	private String projectId;
	private String userId;
	private String status;
	private String deployUrl;
	private String commitSha;
	private String logs;
	private String errorMessage;
	private String createdAt;
	private String finishedAt;

	// Phase 6: Serverless functions
	private String functionUrl;        // Lambda Function URL
	private String lambdaFunctionName; // AWS Lambda function name

	// Phase 7: Preview deployments
	private String branch;             // git branch this deploy came from, e.g. "main", "feature/x"
	private String environment;        // "production" or "preview"

	@DynamoDbPartitionKey
	@DynamoDbAttribute("deploymentId")
	public String getDeploymentId() {
		return this.deploymentId;
	}

	public void setDeploymentId(String deploymentId) {
		this.deploymentId = deploymentId;
	}

	@DynamoDbSecondaryPartitionKey(indexNames = PROJECT_INDEX)
	@DynamoDbAttribute("projectId")
	public String getProjectId() {
		return this.projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	/** Denormalised from the project so ownership checks don't need a second read. */
	@DynamoDbAttribute("userId")
	public String getUserId() {
		return this.userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	@DynamoDbAttribute("status")
	public String getStatus() {
		return this.status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@DynamoDbAttribute("deployUrl")
	public String getDeployUrl() {
		return this.deployUrl;
	}

	public void setDeployUrl(String deployUrl) {
		this.deployUrl = deployUrl;
	}

	@DynamoDbAttribute("commitSha")
	public String getCommitSha() {
		return this.commitSha;
	}

	public void setCommitSha(String commitSha) {
		this.commitSha = commitSha;
	}

	@DynamoDbAttribute("logs")
	public String getLogs() {
		return this.logs;
	}

	public void setLogs(String logs) {
		this.logs = logs;
	}

	@DynamoDbAttribute("errorMessage")
	public String getErrorMessage() {
		return this.errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	@DynamoDbSecondarySortKey(indexNames = PROJECT_INDEX)
	@DynamoDbAttribute("createdAt")
	public String getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	@DynamoDbAttribute("finishedAt")
	public String getFinishedAt() {
		return this.finishedAt;
	}

	public void setFinishedAt(String finishedAt) {
		this.finishedAt = finishedAt;
	}

	@DynamoDbAttribute("functionUrl")
	public String getFunctionUrl() {
		return this.functionUrl;
	}

	public void setFunctionUrl(String functionUrl) {
		this.functionUrl = functionUrl;
	}

	@DynamoDbAttribute("lambdaFunctionName")
	public String getLambdaFunctionName() {
		return this.lambdaFunctionName;
	}

	public void setLambdaFunctionName(String lambdaFunctionName) {
		this.lambdaFunctionName = lambdaFunctionName;
	}

	@DynamoDbAttribute("branch")
	public String getBranch() {
		return this.branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	@DynamoDbAttribute("environment")
	public String getEnvironment() {
		return this.environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}
}
