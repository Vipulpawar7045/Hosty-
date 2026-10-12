package com.minivercel.domain;

/**
 * Provisions and tracks the TLS certificate backing a custom domain.
 *
 * <p>Phase 8 ships a single {@link AcmCertificateProvisioner} (AWS Certificate Manager, DNS
 * validation). The interface exists so the domain flow can be unit-tested against a fake and so a
 * different CA could slot in later without touching {@link DomainService}.
 */
public interface CertificateProvisioner {

	/**
	 * Requests a DNS-validated certificate for {@code domainName} and returns its ARN together with
	 * the CNAME record that must be published for validation to succeed.
	 *
	 * @return the cert ARN, its current status, and the validation record — never null
	 */
	CertificateInfo request(String domainName);

	/**
	 * Re-reads a certificate's current state (used to poll for issuance after the validation record
	 * is published). The returned validation record fields may be null once a cert is issued.
	 *
	 * @param certificateArn the ARN returned by {@link #request}
	 * @return the current status and (if still pending) validation record — never null
	 */
	CertificateInfo describe(String certificateArn);

	/**
	 * Deletes a certificate (when a domain is removed). A missing certificate is treated as already
	 * gone — this never throws for that case.
	 *
	 * @param certificateArn the ARN to delete
	 */
	void remove(String certificateArn);
}
