package com.minivercel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.CertificateDetail;
import software.amazon.awssdk.services.acm.model.CertificateStatus;
import software.amazon.awssdk.services.acm.model.DeleteCertificateRequest;
import software.amazon.awssdk.services.acm.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.acm.model.DescribeCertificateResponse;
import software.amazon.awssdk.services.acm.model.DomainValidation;
import software.amazon.awssdk.services.acm.model.RecordType;
import software.amazon.awssdk.services.acm.model.RequestCertificateRequest;
import software.amazon.awssdk.services.acm.model.RequestCertificateResponse;
import software.amazon.awssdk.services.acm.model.ResourceNotFoundException;
import software.amazon.awssdk.services.acm.model.ResourceRecord;

/**
 * Unit tests for {@link AcmCertificateProvisioner} against a mocked {@link AcmClient}. No AWS
 * account involved: every ACM call is stubbed, and the polling loop is driven with a tiny attempt
 * budget so the tests stay fast.
 */
class AcmCertificateProvisionerTest {

	private static final String ARN = "arn:aws:acm:us-east-1:123456789012:certificate/abc";
	private static final String RECORD_NAME = "_abc.app.example.com.";
	private static final String RECORD_VALUE = "_xyz.acm-validations.aws.";

	AcmClient acm;
	AcmCertificateProvisioner provisioner;

	@BeforeEach
	void setUp() {
		acm = mock(AcmClient.class);
		// Small poll budget + near-zero delay keeps the polling tests fast.
		provisioner = new AcmCertificateProvisioner(acm, 3, 1L);
	}

	// ── request ──────────────────────────────────────────────────────────────────────

	@Test
	void requestReturnsValidationRecordWhenAvailableImmediately() {
		when(acm.requestCertificate(any(RequestCertificateRequest.class)))
				.thenReturn(RequestCertificateResponse.builder().certificateArn(ARN).build());
		when(acm.describeCertificate(any(DescribeCertificateRequest.class)))
				.thenReturn(describeResp(CertificateStatus.PENDING_VALIDATION, true));

		CertificateInfo info = provisioner.request("app.example.com");

		assertThat(info.certificateArn()).isEqualTo(ARN);
		assertThat(info.status()).isEqualTo(DomainStatus.PENDING_VALIDATION);
		assertThat(info.validationRecordName()).isEqualTo(RECORD_NAME);
		assertThat(info.validationRecordValue()).isEqualTo(RECORD_VALUE);
		assertThat(info.validationRecordType()).isEqualTo("CNAME");

		// The cert is requested for the given name with DNS validation.
		ArgumentCaptor<RequestCertificateRequest> captor =
				ArgumentCaptor.forClass(RequestCertificateRequest.class);
		verify(acm).requestCertificate(captor.capture());
		assertThat(captor.getValue().domainName()).isEqualTo("app.example.com");
		assertThat(captor.getValue().validationMethodAsString()).isEqualTo("DNS");
	}

	@Test
	void requestPollsUntilValidationRecordAppears() {
		when(acm.requestCertificate(any(RequestCertificateRequest.class)))
				.thenReturn(RequestCertificateResponse.builder().certificateArn(ARN).build());
		// ACM attaches the record a beat later: absent on the first describe, present on the second.
		when(acm.describeCertificate(any(DescribeCertificateRequest.class)))
				.thenReturn(describeResp(CertificateStatus.PENDING_VALIDATION, false),
						describeResp(CertificateStatus.PENDING_VALIDATION, true));

		CertificateInfo info = provisioner.request("app.example.com");

		assertThat(info.validationRecordName()).isEqualTo(RECORD_NAME);
		verify(acm, times(2)).describeCertificate(any(DescribeCertificateRequest.class));
	}

	@Test
	void requestReturnsPendingWithoutRecordWhenNeverAttached() {
		when(acm.requestCertificate(any(RequestCertificateRequest.class)))
				.thenReturn(RequestCertificateResponse.builder().certificateArn(ARN).build());
		when(acm.describeCertificate(any(DescribeCertificateRequest.class)))
				.thenReturn(describeResp(CertificateStatus.PENDING_VALIDATION, false));

		CertificateInfo info = provisioner.request("app.example.com");

		assertThat(info.status()).isEqualTo(DomainStatus.PENDING_VALIDATION);
		assertThat(info.validationRecordName()).isNull();
	}

	// ── describe ─────────────────────────────────────────────────────────────────────

	@Test
	void describeMapsIssuedStatusWithNoRecord() {
		when(acm.describeCertificate(any(DescribeCertificateRequest.class)))
				.thenReturn(describeResp(CertificateStatus.ISSUED, false));

		CertificateInfo info = provisioner.describe(ARN);

		assertThat(info.status()).isEqualTo(DomainStatus.ISSUED);
		assertThat(info.validationRecordName()).isNull();
	}

	@Test
	void describeMapsTerminalStatusToFailed() {
		when(acm.describeCertificate(any(DescribeCertificateRequest.class)))
				.thenReturn(describeResp(CertificateStatus.VALIDATION_TIMED_OUT, false));

		CertificateInfo info = provisioner.describe(ARN);

		assertThat(info.status()).isEqualTo(DomainStatus.FAILED);
	}

	// ── remove ───────────────────────────────────────────────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	void removeDeletesCertificate() {
		provisioner.remove(ARN);

		ArgumentCaptor<Consumer<DeleteCertificateRequest.Builder>> captor =
				ArgumentCaptor.forClass(Consumer.class);
		verify(acm).deleteCertificate(captor.capture());

		// Apply the captured consumer to a real builder to prove the right ARN flows through.
		DeleteCertificateRequest.Builder builder = DeleteCertificateRequest.builder();
		captor.getValue().accept(builder);
		assertThat(builder.build().certificateArn()).isEqualTo(ARN);
	}

	@Test
	@SuppressWarnings("unchecked")
	void removeSwallowsResourceNotFound() {
		when(acm.deleteCertificate(any(Consumer.class)))
				.thenThrow(ResourceNotFoundException.builder().build());

		assertThatCode(() -> provisioner.remove(ARN)).doesNotThrowAnyException();
	}

	@Test
	void removeIsNoOpForBlankArn() {
		provisioner.remove("   ");
		verifyNoInteractions(acm);
	}

	// ── Fixtures ─────────────────────────────────────────────────────────────────────

	private static DescribeCertificateResponse describeResp(CertificateStatus status, boolean withRecord) {
		DomainValidation.Builder validation = DomainValidation.builder().domainName("app.example.com");
		if (withRecord) {
			validation.resourceRecord(ResourceRecord.builder()
					.name(RECORD_NAME)
					.value(RECORD_VALUE)
					.type(RecordType.CNAME)
					.build());
		}
		CertificateDetail detail = CertificateDetail.builder()
				.certificateArn(ARN)
				.status(status)
				.domainValidationOptions(validation.build())
				.build();
		return DescribeCertificateResponse.builder().certificate(detail).build();
	}
}
