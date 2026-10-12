package com.minivercel.domain;

/**
 * Publishes DNS records on the user's behalf when their domain is hosted in Route53.
 *
 * <p>Optional: only used when a hosted-zone id is configured. When it isn't, the platform simply
 * returns the validation record for the user to add wherever their DNS lives.
 */
public interface DnsManager {

	/**
	 * Upserts the ACM DNS-validation CNAME into the given hosted zone. Best-effort: implementations
	 * should return {@code false} rather than throwing so a failure here doesn't sink the whole
	 * add-domain flow (the user can always publish the record manually).
	 *
	 * @return true if the record was written
	 */
	boolean upsertValidationRecord(String hostedZoneId, String recordName, String recordValue);
}
