package com.minivercel.user;

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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
public class UserRepository {

	private final DynamoDbTable<User> table;

	private final DynamoDbIndex<User> byEmail;

	private final DynamoDbClient client;

	private final String emailClaimsTable;

	public UserRepository(DynamoDbEnhancedClient enhanced, DynamoDbClient client, TableNames tables) {
		this.table = enhanced.table(tables.users(), TableSchema.fromBean(User.class));
		this.byEmail = this.table.index(User.EMAIL_INDEX);
		this.client = client;
		this.emailClaimsTable = tables.emailClaims();
	}

	public Optional<User> findById(String userId) {
		return Optional.ofNullable(this.table.getItem(Key.builder().partitionValue(userId).build()));
	}

	public Optional<User> findByEmail(String email) {
		var query = QueryEnhancedRequest.builder()
				.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(email)))
				.limit(1)
				.build();

		return this.byEmail.query(query).stream()
				.flatMap(page -> page.items().stream())
				.findFirst();
	}

	/**
	 * Reserves an email address, then writes the user. The reservation is a conditional put that
	 * fails if the address is already claimed — a GSI can't enforce uniqueness on its own, since
	 * secondary indexes in DynamoDB permit duplicate keys.
	 *
	 * @return false if the email was already taken
	 */
	public boolean saveIfEmailAvailable(User user) {
		try {
			this.client.putItem(PutItemRequest.builder()
					.tableName(this.emailClaimsTable)
					.item(java.util.Map.of(
							"email", AttributeValue.fromS(user.getEmail()),
							"userId", AttributeValue.fromS(user.getUserId())))
					.conditionExpression("attribute_not_exists(email)")
					.build());
		}
		catch (ConditionalCheckFailedException ex) {
			return false;
		}

		this.table.putItem(user);
		return true;
	}

	/**
	 * Scans all users (for admin monitoring only).
	 * WARNING: This is a table scan and should not be used in production for large datasets.
	 */
	public List<User> findAll() {
		return this.table.scan().items().stream().toList();
	}
}
