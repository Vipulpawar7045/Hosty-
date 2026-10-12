package com.minivercel.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class DynamoDbConfig {

	@Bean
	DynamoDbClient dynamoDbClient(AppProperties props) {
		var builder = DynamoDbClient.builder().region(Region.of(props.aws().region()));

		if (StringUtils.hasText(props.aws().dynamodbEndpoint())) {
			// DynamoDB Local ignores credentials but the SDK still requires some to be present,
			// so we hand it dummies rather than failing on an empty credential chain.
			builder.endpointOverride(URI.create(props.aws().dynamodbEndpoint()))
					.credentialsProvider(StaticCredentialsProvider.create(
							AwsBasicCredentials.create("local", "local")));
		}
		else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}
		return builder.build();
	}

	@Bean
	DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
		return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
	}

	@Bean
	S3Client s3Client(AppProperties props) {
		var builder = S3Client.builder().region(Region.of(props.aws().region()));

		if (StringUtils.hasText(props.aws().dynamodbEndpoint())) {
			// In local mode (DynamoDB Local) we won't actually call S3, but the bean
			// still needs valid-looking credentials so it boots without an error.
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create("local", "local")));
		}
		else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}
		return builder.build();
	}

	@Bean
	LambdaClient lambdaClient(AppProperties props) {
		var builder = LambdaClient.builder().region(Region.of(props.aws().region()));

		if (StringUtils.hasText(props.aws().dynamodbEndpoint())) {
			// Local mode: never actually calls Lambda, but the bean must construct cleanly.
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create("local", "local")));
		}
		else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}
		return builder.build();
	}

	/**
	 * ACM lives in its own region because certs served through CloudFront are only honored when
	 * issued from {@code us-east-1}, regardless of where the rest of the stack runs.
	 */
	@Bean
	AcmClient acmClient(AppProperties props) {
		var domains = props.aws().domains();
		String region = (domains != null && StringUtils.hasText(domains.certRegion()))
				? domains.certRegion() : "us-east-1";
		var builder = AcmClient.builder().region(Region.of(region));

		if (StringUtils.hasText(props.aws().dynamodbEndpoint())) {
			// Local mode: never actually calls ACM, but the bean must construct cleanly.
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create("local", "local")));
		}
		else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}
		return builder.build();
	}

	/** Route53 is a global service; the SDK still requires a region, so we pin the global one. */
	@Bean
	Route53Client route53Client(AppProperties props) {
		var builder = Route53Client.builder().region(Region.AWS_GLOBAL);

		if (StringUtils.hasText(props.aws().dynamodbEndpoint())) {
			// Local mode: never actually calls Route53, but the bean must construct cleanly.
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create("local", "local")));
		}
		else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}
		return builder.build();
	}
}
