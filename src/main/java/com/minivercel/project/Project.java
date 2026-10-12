package com.minivercel.project;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@DynamoDbBean
public class Project {

	public static final String USER_INDEX = "userId-index";

	private String projectId;
	private String userId;
	private String name;
	private String gitRepoUrl;
	private String buildCommand;
	private String outputDir;
	private String createdAt;
	private String productionBranch;  // Phase 7: pushes here deploy to production; others are previews

	// Phase 6: Serverless functions
	private String functionDir;       // e.g., "api" or "functions"
	private String functionRuntime;   // e.g., "nodejs20.x", "python3.12", "java21"
	private String functionHandler;   // e.g., "index.handler" for Node.js

	@DynamoDbPartitionKey
	@DynamoDbAttribute("projectId")
	public String getProjectId() {
		return this.projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	@DynamoDbSecondaryPartitionKey(indexNames = USER_INDEX)
	@DynamoDbAttribute("userId")
	public String getUserId() {
		return this.userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	@DynamoDbAttribute("name")
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@DynamoDbAttribute("gitRepoUrl")
	public String getGitRepoUrl() {
		return this.gitRepoUrl;
	}

	public void setGitRepoUrl(String gitRepoUrl) {
		this.gitRepoUrl = gitRepoUrl;
	}

	@DynamoDbAttribute("buildCommand")
	public String getBuildCommand() {
		return this.buildCommand;
	}

	public void setBuildCommand(String buildCommand) {
		this.buildCommand = buildCommand;
	}

	@DynamoDbAttribute("outputDir")
	public String getOutputDir() {
		return this.outputDir;
	}

	public void setOutputDir(String outputDir) {
		this.outputDir = outputDir;
	}

	@DynamoDbAttribute("createdAt")
	public String getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	@DynamoDbAttribute("productionBranch")
	public String getProductionBranch() {
		return this.productionBranch;
	}

	public void setProductionBranch(String productionBranch) {
		this.productionBranch = productionBranch;
	}

	@DynamoDbAttribute("functionDir")
	public String getFunctionDir() {
		return this.functionDir;
	}

	public void setFunctionDir(String functionDir) {
		this.functionDir = functionDir;
	}

	@DynamoDbAttribute("functionRuntime")
	public String getFunctionRuntime() {
		return this.functionRuntime;
	}

	public void setFunctionRuntime(String functionRuntime) {
		this.functionRuntime = functionRuntime;
	}

	@DynamoDbAttribute("functionHandler")
	public String getFunctionHandler() {
		return this.functionHandler;
	}

	public void setFunctionHandler(String functionHandler) {
		this.functionHandler = functionHandler;
	}
}
