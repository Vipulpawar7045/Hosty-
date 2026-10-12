package com.minivercel.domain;

/**
 * Lifecycle of a custom domain, tracking its ACM certificate. Stored as the string value on
 * the record. Mirrors the ACM certificate states we care about, collapsing the terminal error
 * states (expired, revoked, timed-out, failed) into {@link #FAILED}.
 */
public enum DomainStatus {

	/** Cert requested; waiting for the DNS validation record to be seen by ACM. */
	PENDING_VALIDATION("pending_validation"),
	/** Cert issued and ready — the domain can be pointed at the CloudFront target. */
	ISSUED("issued"),
	/** Validation or issuance failed; see errorMessage on the record. */
	FAILED("failed"),
	/** Domain detached by the user; the cert has been (or will be) deleted. */
	REMOVED("removed");

	private final String value;

	DomainStatus(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}
}
