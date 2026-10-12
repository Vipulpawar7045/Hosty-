package com.minivercel.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ChangeBatch;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

/**
 * Route53-backed {@link DnsManager}. Upserts the ACM validation CNAME so a domain whose DNS is in
 * Route53 validates without the user touching anything.
 */
@Component
public class Route53DnsManager implements DnsManager {

	private static final Logger log = LoggerFactory.getLogger(Route53DnsManager.class);

	/** Short TTL — validation records are transient and we want changes to propagate quickly. */
	private static final long TTL_SECONDS = 300L;

	private final Route53Client route53;

	public Route53DnsManager(Route53Client route53) {
		this.route53 = route53;
	}

	@Override
	public boolean upsertValidationRecord(String hostedZoneId, String recordName, String recordValue) {
		if (hostedZoneId == null || hostedZoneId.isBlank()
				|| recordName == null || recordValue == null) {
			return false;
		}
		try {
			ResourceRecordSet recordSet = ResourceRecordSet.builder()
					.name(recordName)
					.type(RRType.CNAME)
					.ttl(TTL_SECONDS)
					.resourceRecords(ResourceRecord.builder().value(recordValue).build())
					.build();

			this.route53.changeResourceRecordSets(ChangeResourceRecordSetsRequest.builder()
					.hostedZoneId(hostedZoneId)
					.changeBatch(ChangeBatch.builder()
							.changes(Change.builder()
									.action(ChangeAction.UPSERT)
									.resourceRecordSet(recordSet)
									.build())
							.build())
					.build());
			log.info("Upserted Route53 validation record {} in zone {}", recordName, hostedZoneId);
			return true;
		}
		catch (Exception ex) {
			// Best-effort: the user can still publish the record manually.
			log.warn("Failed to upsert Route53 validation record {} in zone {}",
					recordName, hostedZoneId, ex);
			return false;
		}
	}
}
