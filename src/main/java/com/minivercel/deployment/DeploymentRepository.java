package com.minivercel.deployment;

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
public class DeploymentRepository {

	private final DynamoDbTable<Deployment> table;

	private final DynamoDbIndex<Deployment> byProject;

	public DeploymentRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
		this.table = enhanced.table(tables.deployments(), TableSchema.fromBean(Deployment.class));
		this.byProject = this.table.index(Deployment.PROJECT_INDEX);
	}

	public Optional<Deployment> findById(String deploymentId) {
		return Optional.ofNullable(this.table.getItem(Key.builder().partitionValue(deploymentId).build()));
	}

	/** Newest first — the index sorts ascending by createdAt, so we scan it backwards. */
	public List<Deployment> findByProjectId(String projectId, int limit) {
		var query = QueryEnhancedRequest.builder()
				.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(projectId)))
				.scanIndexForward(false)
				.limit(limit)
				.build();

		return this.byProject.query(query).stream()
				.flatMap(page -> page.items().stream())
				.limit(limit)
				.toList();
	}

	public void save(Deployment deployment) {
		this.table.putItem(deployment);
	}
}
