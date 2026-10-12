package com.minivercel.domain.dto;

import com.minivercel.domain.Domain;

/**
 * Public view of a custom domain. The {@code validationRecord*} fields tell the user exactly which
 * CNAME to publish; {@code cloudfrontTarget} is where they point the domain once the cert is issued.
 * The internal {@code userId} is deliberately omitted.
 */
public record DomainResponse(
		String domainId,
		String projectId,
		String domainName,
		String status,
		String certificateArn,
		String validationRecordName,
		String validationRecordValue,
		String validationRecordType,
		String cloudfrontTarget,
		String createdAt,
		String validatedAt,
		String errorMessage) {

	public static DomainResponse from(Domain d) {
		return new DomainResponse(
				d.getDomainId(),
				d.getProjectId(),
				d.getDomainName(),
				d.getStatus(),
				d.getCertificateArn(),
				d.getValidationRecordName(),
				d.getValidationRecordValue(),
				d.getValidationRecordType(),
				d.getCloudfrontTarget(),
				d.getCreatedAt(),
				d.getValidatedAt(),
				d.getErrorMessage());
	}
}
