package com.minivercel.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.CertificateDetail;
import software.amazon.awssdk.services.acm.model.CertificateStatus;
import software.amazon.awssdk.services.acm.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.acm.model.DomainValidation;
import software.amazon.awssdk.services.acm.model.RequestCertificateRequest;
import software.amazon.awssdk.services.acm.model.ResourceNotFoundException;
import software.amazon.awssdk.services.acm.model.ResourceRecord;
import software.amazon.awssdk.services.acm.model.ValidationMethod;

/**
 * Provisions certificates through AWS Certificate Manager using DNS validation.
 *
 * <p>ACM populates the validation record asynchronously — it isn't present in the response to
 * {@code requestCertificate}, and appears a moment later on {@code describeCertificate}. So
 * {@link #request} polls briefly for it; if it still isn't ready, we return a pending result with
 * no record and the caller can pick it up on a later {@link #describe}.
 */
@Component
public class AcmCertificateProvisioner implements CertificateProvisioner {

	private static final Logger log = LoggerFactory.getLogger(AcmCertificateProvisioner.class);

	private final AcmClient acm;

	private final int maxAttempts;

	private final long delayMillis;

	@Autowired
	public AcmCertificateProvisioner(AcmClient acm) {
		this(acm, 8, 250L);
	}

	// Package-private, for tests to drive the polling loop with a tiny attempt budget. Because this
	// second constructor exists, Spring can't pick one on its own — @Autowired above disambiguates.
	AcmCertificateProvisioner(AcmClient acm, int maxAttempts, long delayMillis) {
		this.acm = acm;
		this.maxAttempts = maxAttempts;
		this.delayMillis = delayMillis;
	}

	@Override
	public CertificateInfo request(String domainName) {
		String arn = this.acm.requestCertificate(RequestCertificateRequest.builder()
				.domainName(domainName)
				.validationMethod(ValidationMethod.DNS)
				.build())
				.certificateArn();
		log.info("Requested ACM certificate {} for {}", arn, domainName);

		// Poll for the validation record, which ACM attaches a beat after the request.
		for (int attempt = 0; attempt < this.maxAttempts; attempt++) {
			CertificateInfo info = describe(arn);
			if (info.validationRecordName() != null || info.status() != DomainStatus.PENDING_VALIDATION) {
				return info;
			}
			if (!sleep()) {
				break;
			}
		}
		return describe(arn);
	}

	@Override
	public CertificateInfo describe(String certificateArn) {
		CertificateDetail cert = this.acm.describeCertificate(DescribeCertificateRequest.builder()
				.certificateArn(certificateArn)
				.build())
				.certificate();

		ResourceRecord record = cert.domainValidationOptions().stream()
				.map(DomainValidation::resourceRecord)
				.filter(r -> r != null)
				.findFirst()
				.orElse(null);

		DomainStatus status = mapStatus(cert.status());
		if (record == null) {
			return new CertificateInfo(certificateArn, status, null, null, null);
		}
		return new CertificateInfo(certificateArn, status,
				record.name(), record.value(), record.typeAsString());
	}

	@Override
	public void remove(String certificateArn) {
		if (certificateArn == null || certificateArn.isBlank()) {
			return;
		}
		try {
			this.acm.deleteCertificate(b -> b.certificateArn(certificateArn));
			log.info("Deleted ACM certificate {}", certificateArn);
		}
		catch (ResourceNotFoundException ex) {
			log.debug("ACM certificate {} already gone", certificateArn);
		}
	}

	private static DomainStatus mapStatus(CertificateStatus status) {
		return switch (status) {
			case ISSUED -> DomainStatus.ISSUED;
			case PENDING_VALIDATION -> DomainStatus.PENDING_VALIDATION;
			default -> DomainStatus.FAILED;
		};
	}

	/** @return false if the wait was interrupted, so the caller stops polling. */
	private boolean sleep() {
		try {
			Thread.sleep(this.delayMillis);
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
