package com.minivercel.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

/**
 * A custom domain attached to a project. Each one owns an ACM certificate, validated over DNS.
 * The {@code validationRecord*} fields are the CNAME the user (or Route53) must publish so ACM
 * can prove ownership before the cert is issued.
 */
@DynamoDbBean
public class Domain {

	/** Lists every domain on a project without scanning the table. */
	public static final String PROJECT_INDEX = "projectId-index";

	private String domainId;
	private String projectId;
	private String userId;
	private String domainName;
	private String status;
	private String certificateArn;
	private String validationRecordName;
	private String validationRecordValue;
	private String validationRecordType;
	/** CloudFront target the user CNAMEs their domain at once the cert is issued. */
	private String cloudfrontTarget;
	private String createdAt;
	private String validatedAt;
	private String errorMessage;

	@DynamoDbPartitionKey
	@DynamoDbAttribute("domainId")
	public String getDomainId() {
		return this.domainId;
	}

	public void setDomainId(String domainId) {
		this.domainId = domainId;
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

	@DynamoDbAttribute("domainName")
	public String getDomainName() {
		return this.domainName;
	}

	public void setDomainName(String domainName) {
		this.domainName = domainName;
	}

	@DynamoDbAttribute("status")
	public String getStatus() {
		return this.status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@DynamoDbAttribute("certificateArn")
	public String getCertificateArn() {
		return this.certificateArn;
	}

	public void setCertificateArn(String certificateArn) {
		this.certificateArn = certificateArn;
	}

	@DynamoDbAttribute("validationRecordName")
	public String getValidationRecordName() {
		return this.validationRecordName;
	}

	public void setValidationRecordName(String validationRecordName) {
		this.validationRecordName = validationRecordName;
	}

	@DynamoDbAttribute("validationRecordValue")
	public String getValidationRecordValue() {
		return this.validationRecordValue;
	}

	public void setValidationRecordValue(String validationRecordValue) {
		this.validationRecordValue = validationRecordValue;
	}

	@DynamoDbAttribute("validationRecordType")
	public String getValidationRecordType() {
		return this.validationRecordType;
	}

	public void setValidationRecordType(String validationRecordType) {
		this.validationRecordType = validationRecordType;
	}

	@DynamoDbAttribute("cloudfrontTarget")
	public String getCloudfrontTarget() {
		return this.cloudfrontTarget;
	}

	public void setCloudfrontTarget(String cloudfrontTarget) {
		this.cloudfrontTarget = cloudfrontTarget;
	}

	@DynamoDbAttribute("createdAt")
	public String getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	@DynamoDbAttribute("validatedAt")
	public String getValidatedAt() {
		return this.validatedAt;
	}

	public void setValidatedAt(String validatedAt) {
		this.validatedAt = validatedAt;
	}

	@DynamoDbAttribute("errorMessage")
	public String getErrorMessage() {
		return this.errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
