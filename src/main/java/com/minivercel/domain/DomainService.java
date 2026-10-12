package com.minivercel.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.minivercel.common.BadRequestException;
import com.minivercel.common.NotFoundException;
import com.minivercel.config.AppProperties;
import com.minivercel.domain.dto.DomainResponse;
import com.minivercel.project.ProjectService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/**
 * Attaches custom domains to projects: provisions an ACM certificate, surfaces (or auto-publishes)
 * the DNS-validation record, and tracks the cert to issuance.
 *
 * <p>Ownership is enforced through {@link ProjectService#requireOwned} on attach and against the
 * denormalised {@code userId} on the domain record thereafter — a caller can only see or mutate
 * domains on projects they own.
 */
@Service
public class DomainService {

	private static final Logger log = LoggerFactory.getLogger(DomainService.class);

	private final DomainRepository domainRepository;

	private final CertificateProvisioner certificateProvisioner;

	private final DnsManager dnsManager;

	private final ProjectService projectService;

	private final AppProperties.Domains config;

	public DomainService(DomainRepository domainRepository, CertificateProvisioner certificateProvisioner,
			DnsManager dnsManager, ProjectService projectService, AppProperties props) {
		this.domainRepository = domainRepository;
		this.certificateProvisioner = certificateProvisioner;
		this.dnsManager = dnsManager;
		this.projectService = projectService;
		this.config = props.aws().domains();
	}

	public DomainResponse addDomain(String projectId, String userId, String domainName) {
		projectService.requireOwned(projectId, userId);

		String normalized = domainName.trim().toLowerCase();
		domainRepository.findByDomainName(normalized).ifPresent(existing -> {
			throw new BadRequestException("Domain " + normalized + " is already attached to a project");
		});

		CertificateInfo cert = certificateProvisioner.request(normalized);

		Domain domain = new Domain();
		domain.setDomainId(UUID.randomUUID().toString());
		domain.setProjectId(projectId);
		domain.setUserId(userId);
		domain.setDomainName(normalized);
		domain.setStatus(cert.status().value());
		domain.setCertificateArn(cert.certificateArn());
		domain.setValidationRecordName(cert.validationRecordName());
		domain.setValidationRecordValue(cert.validationRecordValue());
		domain.setValidationRecordType(cert.validationRecordType());
		domain.setCloudfrontTarget(cloudfrontTarget());
		domain.setCreatedAt(Instant.now().toString());

		autoPublishValidationRecord(cert);

		domainRepository.save(domain);
		log.info("Attached domain {} to project {} (cert {})", normalized, projectId, cert.certificateArn());
		return DomainResponse.from(domain);
	}

	public List<DomainResponse> listDomains(String projectId, String userId) {
		projectService.requireOwned(projectId, userId);
		return domainRepository.findByProjectId(projectId).stream()
				.map(DomainResponse::from)
				.toList();
	}

	public DomainResponse getDomain(String domainId, String userId) {
		return DomainResponse.from(requireOwned(domainId, userId));
	}

	/**
	 * Re-reads the certificate from ACM and folds any change back onto the record: fills in the
	 * validation CNAME once ACM attaches it, and stamps {@code validatedAt} on first issuance.
	 * A removed domain is returned untouched.
	 */
	public DomainResponse refreshStatus(String domainId, String userId) {
		Domain domain = requireOwned(domainId, userId);
		if (DomainStatus.REMOVED.value().equals(domain.getStatus()) || domain.getCertificateArn() == null) {
			return DomainResponse.from(domain);
		}

		CertificateInfo cert = certificateProvisioner.describe(domain.getCertificateArn());

		boolean wasIssued = DomainStatus.ISSUED.value().equals(domain.getStatus());
		domain.setStatus(cert.status().value());
		if (cert.validationRecordName() != null) {
			domain.setValidationRecordName(cert.validationRecordName());
			domain.setValidationRecordValue(cert.validationRecordValue());
			domain.setValidationRecordType(cert.validationRecordType());
		}
		if (!wasIssued && cert.status() == DomainStatus.ISSUED) {
			domain.setValidatedAt(Instant.now().toString());
		}

		domainRepository.save(domain);
		return DomainResponse.from(domain);
	}

	/**
	 * Detaches a domain: deletes its certificate (best-effort) and marks the record removed so the
	 * hostname frees up for reuse while history is retained.
	 */
	public void removeDomain(String domainId, String userId) {
		Domain domain = requireOwned(domainId, userId);
		certificateProvisioner.remove(domain.getCertificateArn());
		domain.setStatus(DomainStatus.REMOVED.value());
		domainRepository.save(domain);
		log.info("Removed domain {} from project {}", domain.getDomainName(), domain.getProjectId());
	}

	private void autoPublishValidationRecord(CertificateInfo cert) {
		String zone = (this.config != null) ? this.config.route53HostedZoneId() : null;
		if (zone == null || zone.isBlank() || cert.validationRecordName() == null) {
			return;
		}
		dnsManager.upsertValidationRecord(zone, cert.validationRecordName(), cert.validationRecordValue());
	}

	private String cloudfrontTarget() {
		return (this.config != null) ? this.config.cloudfrontTarget() : null;
	}

	private Domain requireOwned(String domainId, String userId) {
		return domainRepository.findById(domainId)
				.filter(d -> userId.equals(d.getUserId()))
				.orElseThrow(() -> new NotFoundException("No domain with id " + domainId));
	}
}
