package com.minivercel.project;

import java.util.Comparator;
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
public class ProjectRepository {

	private final DynamoDbTable<Project> table;

	private final DynamoDbIndex<Project> byUser;

	public ProjectRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
		this.table = enhanced.table(tables.projects(), TableSchema.fromBean(Project.class));
		this.byUser = this.table.index(Project.USER_INDEX);
	}

	public Optional<Project> findById(String projectId) {
		return Optional.ofNullable(this.table.getItem(Key.builder().partitionValue(projectId).build()));
	}

	public List<Project> findByUserId(String userId) {
		var query = QueryEnhancedRequest.builder()
				.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(userId)))
				.build();

		return this.byUser.query(query).stream()
				.flatMap(page -> page.items().stream())
				.sorted(Comparator.comparing(Project::getCreatedAt).reversed())
				.toList();
	}

	public Optional<Project> findByGitRepoUrl(String gitRepoUrl) {
		// Scan all projects and find by gitRepoUrl
		// Note: This is inefficient for large datasets - in production, add a GSI on gitRepoUrl
		return this.table.scan().items().stream()
				.filter(p -> p.getGitRepoUrl().equals(gitRepoUrl))
				.findFirst();
	}

	public void save(Project project) {
		this.table.putItem(project);
	}

	public void deleteById(String projectId) {
		this.table.deleteItem(Key.builder().partitionValue(projectId).build());
	}

	/**
	 * Scans all projects (for admin monitoring only).
	 * WARNING: This is a table scan and should not be used in production for large datasets.
	 */
	public List<Project> findAll() {
		return this.table.scan().items().stream().toList();
	}
}
