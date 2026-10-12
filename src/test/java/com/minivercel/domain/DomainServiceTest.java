package com.minivercel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.minivercel.common.BadRequestException;
import com.minivercel.common.NotFoundException;
import com.minivercel.config.AppProperties;
import com.minivercel.domain.dto.DomainResponse;
import com.minivercel.project.Project;
import com.minivercel.project.ProjectService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Behavioral tests for {@link DomainService} with mocked collaborators. Covers cert provisioning on
 * attach, hostname normalisation + uniqueness, Route53 auto-publish gating, status refresh
 * (issuance stamping, record fill-in, removed no-op), teardown, and per-user ownership isolation.
 */
class DomainServiceTest {

	private static final String PROJECT_ID = "proj1";
	private static final String USER_ID = "user1";
	private static final String CERT_ARN = "arn:aws:acm:us-east-1:123456789012:certificate/abc";
	private static final String CF_TARGET = "d123.cloudfront.net";

	DomainRepository domainRepository;
	CertificateProvisioner certificateProvisioner;
	DnsManager dnsManager;
	ProjectService projectService;

	@BeforeEach
	void setUp() {
		domainRepository = mock(DomainRepository.class);
		certificateProvisioner = mock(CertificateProvisioner.class);
		dnsManager = mock(DnsManager.class);
		projectService = mock(ProjectService.class);
		when(projectService.requireOwned(PROJECT_ID, USER_ID)).thenReturn(new Project());
	}

	// ── addDomain ──────────────────────────────────────────────────────────────────────

	@Test
	void addDomainProvisionsCertNormalisesNameAndPersistsPendingDomain() {
		DomainService service = newService("", CF_TARGET);
		when(domainRepository.findByDomainName("app.example.com")).thenReturn(Optional.empty());
		when(certificateProvisioner.request("app.example.com")).thenReturn(pendingCert());

		DomainResponse resp = service.addDomain(PROJECT_ID, USER_ID, "  App.Example.COM ");

		// Response reflects the normalised name and the pending cert.
		assertThat(resp.domainName()).isEqualTo("app.example.com");
		assertThat(resp.status()).isEqualTo(DomainStatus.PENDING_VALIDATION.value());
		assertThat(resp.certificateArn()).isEqualTo(CERT_ARN);
		assertThat(resp.validationRecordName()).isEqualTo("_v.app.example.com.");
		assertThat(resp.validationRecordType()).isEqualTo("CNAME");
		assertThat(resp.cloudfrontTarget()).isEqualTo(CF_TARGET);

		verify(projectService).requireOwned(PROJECT_ID, USER_ID);

		ArgumentCaptor<Domain> captor = ArgumentCaptor.forClass(Domain.class);
		verify(domainRepository).save(captor.capture());
		Domain saved = captor.getValue();
		assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
		assertThat(saved.getUserId()).isEqualTo(USER_ID);
		assertThat(saved.getDomainName()).isEqualTo("app.example.com");
		assertThat(saved.getDomainId()).isNotBlank();
		assertThat(saved.getCreatedAt()).isNotBlank();

		// No hosted zone configured → the record is left for the user to publish.
		verifyNoInteractions(dnsManager);
	}

	@Test
	void addDomainAutoPublishesValidationRecordWhenZoneConfigured() {
		DomainService service = newService("Z123ABC", CF_TARGET);
		when(domainRepository.findByDomainName("app.example.com")).thenReturn(Optional.empty());
		when(certificateProvisioner.request("app.example.com")).thenReturn(pendingCert());

		service.addDomain(PROJECT_ID, USER_ID, "app.example.com");

		verify(dnsManager).upsertValidationRecord("Z123ABC", "_v.app.example.com.", "_t.acm-validations.aws.");
	}

	@Test
	void addDomainSkipsAutoPublishWhenRecordNotYetAvailable() {
		DomainService service = newService("Z123ABC", CF_TARGET);
		when(domainRepository.findByDomainName("app.example.com")).thenReturn(Optional.empty());
		// Cert requested but ACM hasn't attached the validation record yet.
		when(certificateProvisioner.request("app.example.com")).thenReturn(
				new CertificateInfo(CERT_ARN, DomainStatus.PENDING_VALIDATION, null, null, null));

		service.addDomain(PROJECT_ID, USER_ID, "app.example.com");

		verifyNoInteractions(dnsManager);
	}

	@Test
	void addDomainRejectsDuplicateHostname() {
		DomainService service = newService("", CF_TARGET);
		when(domainRepository.findByDomainName("taken.example.com"))
				.thenReturn(Optional.of(domain("dX", USER_ID, "taken.example.com", DomainStatus.ISSUED)));

		assertThatThrownBy(() -> service.addDomain(PROJECT_ID, USER_ID, "Taken.Example.com"))
				.isInstanceOf(BadRequestException.class);

		verify(certificateProvisioner, never()).request(any());
		verify(domainRepository, never()).save(any());
	}

	@Test
	void addDomainPropagatesOwnershipFailure() {
		DomainService service = newService("", CF_TARGET);
		when(projectService.requireOwned(PROJECT_ID, USER_ID))
				.thenThrow(new NotFoundException("No project with id " + PROJECT_ID));

		assertThatThrownBy(() -> service.addDomain(PROJECT_ID, USER_ID, "app.example.com"))
				.isInstanceOf(NotFoundException.class);

		verifyNoInteractions(certificateProvisioner);
		verify(domainRepository, never()).save(any());
	}

	// ── list / get ─────────────────────────────────────────────────────────────────────

	@Test
	void listDomainsEnforcesOwnershipAndMaps() {
		DomainService service = newService("", CF_TARGET);
		when(domainRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(
				domain("d1", USER_ID, "a.example.com", DomainStatus.ISSUED),
				domain("d2", USER_ID, "b.example.com", DomainStatus.PENDING_VALIDATION)));

		List<DomainResponse> result = service.listDomains(PROJECT_ID, USER_ID);

		assertThat(result).extracting(DomainResponse::domainName)
				.containsExactly("a.example.com", "b.example.com");
		verify(projectService).requireOwned(PROJECT_ID, USER_ID);
	}

	@Test
	void getDomainReturnsOwnedDomain() {
		DomainService service = newService("", CF_TARGET);
		when(domainRepository.findById("d1"))
				.thenReturn(Optional.of(domain("d1", USER_ID, "a.example.com", DomainStatus.ISSUED)));

		assertThat(service.getDomain("d1", USER_ID).domainName()).isEqualTo("a.example.com");
	}

	// ── refreshStatus ────────────────────────────────────────────────────────────────────

	@Test
	void refreshStampsValidatedAtOnFirstIssuance() {
		DomainService service = newService("", CF_TARGET);
		Domain d = domain("d1", USER_ID, "a.example.com", DomainStatus.PENDING_VALIDATION);
		d.setCertificateArn(CERT_ARN);
		when(domainRepository.findById("d1")).thenReturn(Optional.of(d));
		when(certificateProvisioner.describe(CERT_ARN))
				.thenReturn(new CertificateInfo(CERT_ARN, DomainStatus.ISSUED, null, null, null));

		DomainResponse resp = service.refreshStatus("d1", USER_ID);

		assertThat(resp.status()).isEqualTo(DomainStatus.ISSUED.value());
		assertThat(resp.validatedAt()).isNotBlank();
		verify(domainRepository).save(d);
	}

	@Test
	void refreshDoesNotRestampWhenAlreadyIssued() {
		DomainService service = newService("", CF_TARGET);
		Domain d = domain("d1", USER_ID, "a.example.com", DomainStatus.ISSUED);
		d.setCertificateArn(CERT_ARN);
		d.setValidatedAt("2020-01-01T00:00:00Z");
		when(domainRepository.findById("d1")).thenReturn(Optional.of(d));
		when(certificateProvisioner.describe(CERT_ARN))
				.thenReturn(new CertificateInfo(CERT_ARN, DomainStatus.ISSUED, null, null, null));

		DomainResponse resp = service.refreshStatus("d1", USER_ID);

		assertThat(resp.validatedAt()).isEqualTo("2020-01-01T00:00:00Z");
	}

	@Test
	void refreshFillsValidationRecordWhenAcmAttachesIt() {
		DomainService service = newService("", CF_TARGET);
		Domain d = domain("d1", USER_ID, "a.example.com", DomainStatus.PENDING_VALIDATION);
		d.setCertificateArn(CERT_ARN);
		when(domainRepository.findById("d1")).thenReturn(Optional.of(d));
		when(certificateProvisioner.describe(CERT_ARN)).thenReturn(new CertificateInfo(
				CERT_ARN, DomainStatus.PENDING_VALIDATION, "_v.a.example.com.", "_t.acm-validations.aws.", "CNAME"));

		DomainResponse resp = service.refreshStatus("d1", USER_ID);

		assertThat(resp.status()).isEqualTo(DomainStatus.PENDING_VALIDATION.value());
		assertThat(resp.validationRecordName()).isEqualTo("_v.a.example.com.");
		assertThat(resp.validatedAt()).isNull();
	}

	@Test
	void refreshIsNoOpForRemovedDomain() {
		DomainService service = newService("", CF_TARGET);
		Domain d = domain("d1", USER_ID, "a.example.com", DomainStatus.REMOVED);
		d.setCertificateArn(CERT_ARN);
		when(domainRepository.findById("d1")).thenReturn(Optional.of(d));

		DomainResponse resp = service.refreshStatus("d1", USER_ID);

		assertThat(resp.status()).isEqualTo(DomainStatus.REMOVED.value());
		verify(certificateProvisioner, never()).describe(any());
		verify(domainRepository, never()).save(any());
	}

	// ── removeDomain ─────────────────────────────────────────────────────────────────────

	@Test
	void removeDomainDeletesCertAndMarksRemoved() {
		DomainService service = newService("", CF_TARGET);
		Domain d = domain("d1", USER_ID, "a.example.com", DomainStatus.ISSUED);
		d.setCertificateArn(CERT_ARN);
		when(domainRepository.findById("d1")).thenReturn(Optional.of(d));

		service.removeDomain("d1", USER_ID);

		verify(certificateProvisioner).remove(CERT_ARN);
		assertThat(d.getStatus()).isEqualTo(DomainStatus.REMOVED.value());
		verify(domainRepository).save(d);
	}

	// ── ownership isolation ────────────────────────────────────────────────────────────────

	@Test
	void operationsOnAnotherUsersDomainReportNotFound() {
		DomainService service = newService("", CF_TARGET);
		// The record exists but belongs to someone else — must read as not-found, never forbidden.
		when(domainRepository.findById("d1"))
				.thenReturn(Optional.of(domain("d1", "someone-else", "a.example.com", DomainStatus.ISSUED)));

		assertThatThrownBy(() -> service.getDomain("d1", USER_ID)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.refreshStatus("d1", USER_ID)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.removeDomain("d1", USER_ID)).isInstanceOf(NotFoundException.class);

		verify(certificateProvisioner, never()).remove(any());
		verify(domainRepository, never()).save(any());
	}

	// ── Fixtures ───────────────────────────────────────────────────────────────────────────

	private DomainService newService(String hostedZone, String cloudfrontTarget) {
		return new DomainService(domainRepository, certificateProvisioner, dnsManager, projectService,
				props(hostedZone, cloudfrontTarget));
	}

	private static CertificateInfo pendingCert() {
		return new CertificateInfo(CERT_ARN, DomainStatus.PENDING_VALIDATION,
				"_v.app.example.com.", "_t.acm-validations.aws.", "CNAME");
	}

	private static Domain domain(String id, String userId, String name, DomainStatus status) {
		Domain d = new Domain();
		d.setDomainId(id);
		d.setProjectId(PROJECT_ID);
		d.setUserId(userId);
		d.setDomainName(name);
		d.setStatus(status.value());
		return d;
	}

	private static AppProperties props(String hostedZone, String cloudfrontTarget) {
		return new AppProperties(
				new AppProperties.Jwt("test-secret-test-secret-test-secret-test-secret", Duration.ofHours(1)),
				new AppProperties.Aws("ap-south-1", null, "mv-", "bucket", "https://base.example.com",
						new AppProperties.Lambda("arn:role", "nodejs20.x", "index.handler", 30, 256, "NONE"),
						new AppProperties.Domains("us-east-1", cloudfrontTarget, hostedZone)),
				new AppProperties.Build("./artifacts", Duration.ofMinutes(10), 104_857_600L,
						List.of("github.com"), List.of("npm run build")),
				new AppProperties.Webhook("secret"),
				new AppProperties.Limits(10, 10, 3));
	}
}
