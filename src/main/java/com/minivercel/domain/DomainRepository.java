package com.minivercel.domain;

import java.util.List;
import java.util.Optional;

import com.minivercel.config.TableNames;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
public class DomainRepository {

	private final DynamoDbTable<Domain> table;

	private final DynamoDbIndex<Domain> byProject;

	public DomainRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
		this.table = enhanced.table(tables.domains(), TableSchema.fromBean(Domain.class));
		this.byProject = this.table.index(Domain.PROJECT_INDEX);
	}

	public Optional<Domain> findById(String domainId) {
		return Optional.ofNullable(this.table.getItem(Key.builder().partitionValue(domainId).build()));
	}

	public List<Domain> findByProjectId(String projectId) {
		var query = QueryEnhancedRequest.builder()
				.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(projectId)))
				.build();

		return this.byProject.query(query).stream()
				.flatMap(page -> page.items().stream())
				.toList();
	}

	/**
	 * Finds a domain by its hostname across all projects. A hostname maps to at most one active
	 * domain (uniqueness is enforced at the service layer), so this returns the first match.
	 * Uses a table scan — fine at this scale; add a GSI on domainName if the table grows large.
	 */
	public Optional<Domain> findByDomainName(String domainName) {
		return this.table.scan().items().stream()
				.filter(d -> domainName.equalsIgnoreCase(d.getDomainName())
						&& !DomainStatus.REMOVED.value().equals(d.getStatus()))
				.findFirst();
	}

	public void save(Domain domain) {
		this.table.putItem(domain);
	}

	public void deleteById(String domainId) {
		this.table.deleteItem(Key.builder().partitionValue(domainId).build());
	}
}
