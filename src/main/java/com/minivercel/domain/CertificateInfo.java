package com.minivercel.domain;

/**
 * A certificate's ARN, mapped status, and — while validation is pending — the DNS CNAME record
 * that proves domain ownership to the CA.
 *
 * @param certificateArn        the ACM certificate ARN
 * @param status                mapped lifecycle status ({@code PENDING_VALIDATION}/{@code ISSUED}/{@code FAILED})
 * @param validationRecordName  CNAME record name to publish (null once issued / not yet known)
 * @param validationRecordValue CNAME record value to publish (null once issued / not yet known)
 * @param validationRecordType  record type, typically {@code CNAME} (null once issued / not yet known)
 */
public record CertificateInfo(
		String certificateArn,
		DomainStatus status,
		String validationRecordName,
		String validationRecordValue,
		String validationRecordType) {
}
