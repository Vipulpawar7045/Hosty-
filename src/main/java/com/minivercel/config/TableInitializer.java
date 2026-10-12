package com.minivercel.config;

import java.util.List;

import com.minivercel.deployment.Deployment;
import com.minivercel.domain.Domain;
import com.minivercel.project.Project;
import com.minivercel.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Creates the three tables and their indexes on startup if they don't already exist.
 *
 * <p>Everything is on-demand ({@link BillingMode#PAY_PER_REQUEST}) so there is no provisioned
 * capacity sitting around costing money when the platform is idle.
 */
@Configuration
public class TableInitializer {

	private static final Logger log = LoggerFactory.getLogger(TableInitializer.class);

	@Bean
	ApplicationRunner createTables(DynamoDbClient client, TableNames tables) {
		return args -> {
			createIfAbsent(client, CreateTableRequest.builder()
					.tableName(tables.users())
					.billingMode(BillingMode.PAY_PER_REQUEST)
					.attributeDefinitions(stringAttr("userId"), stringAttr("email"))
					.keySchema(hashKey("userId"))
					.globalSecondaryIndexes(gsi(User.EMAIL_INDEX, "email", null))
					.build());

			createIfAbsent(client, CreateTableRequest.builder()
					.tableName(tables.projects())
					.billingMode(BillingMode.PAY_PER_REQUEST)
					.attributeDefinitions(stringAttr("projectId"), stringAttr("userId"))
					.keySchema(hashKey("projectId"))
					.globalSecondaryIndexes(gsi(Project.USER_INDEX, "userId", null))
					.build());

			createIfAbsent(client, CreateTableRequest.builder()
					.tableName(tables.deployments())
					.billingMode(BillingMode.PAY_PER_REQUEST)
					.attributeDefinitions(stringAttr("deploymentId"), stringAttr("projectId"),
							stringAttr("createdAt"))
					.keySchema(hashKey("deploymentId"))
					.globalSecondaryIndexes(
							gsi(Deployment.PROJECT_INDEX, "projectId", "createdAt"))
					.build());

			// Email uniqueness is enforced by a conditional put on this table; a GSI alone
			// cannot guarantee it.
			createIfAbsent(client, CreateTableRequest.builder()
					.tableName(tables.emailClaims())
					.billingMode(BillingMode.PAY_PER_REQUEST)
					.attributeDefinitions(stringAttr("email"))
					.keySchema(hashKey("email"))
					.build());

			createIfAbsent(client, CreateTableRequest.builder()
					.tableName(tables.domains())
					.billingMode(BillingMode.PAY_PER_REQUEST)
					.attributeDefinitions(stringAttr("domainId"), stringAttr("projectId"))
					.keySchema(hashKey("domainId"))
					.globalSecondaryIndexes(gsi(Domain.PROJECT_INDEX, "projectId", null))
					.build());
		};
	}

	private void createIfAbsent(DynamoDbClient client, CreateTableRequest request) {
		try {
			client.createTable(request);
			log.info("Created DynamoDB table {}", request.tableName());
		}
		catch (ResourceInUseException ex) {
			log.debug("Table {} already exists", request.tableName());
		}
	}

	private static AttributeDefinition stringAttr(String name) {
		return AttributeDefinition.builder()
				.attributeName(name)
				.attributeType(ScalarAttributeType.S)
				.build();
	}

	private static KeySchemaElement hashKey(String name) {
		return KeySchemaElement.builder().attributeName(name).keyType(KeyType.HASH).build();
	}

	private static GlobalSecondaryIndex gsi(String indexName, String hash, String range) {
		List<KeySchemaElement> keys = (range == null) ? List.of(hashKey(hash))
				: List.of(hashKey(hash),
						KeySchemaElement.builder().attributeName(range).keyType(KeyType.RANGE).build());

		return GlobalSecondaryIndex.builder()
				.indexName(indexName)
				.keySchema(keys)
				.projection(Projection.builder().projectionType(ProjectionType.ALL).build())
				.build();
	}
}
