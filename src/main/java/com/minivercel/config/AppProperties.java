package com.minivercel.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables in one place. Bound from the {@code minivercel.*} tree in application.yml.
 */
@ConfigurationProperties(prefix = "minivercel")
public record AppProperties(Jwt jwt, Aws aws, Build build, Webhook webhook, Limits limits) {

	public record Jwt(
			/** HS256 signing key. Must be at least 32 bytes; override in every real deployment. */
			String secret,
			Duration expiry) {
	}

	public record Aws(
			String region,
			/** When set, points the DynamoDB client at DynamoDB Local instead of real AWS. */
			String dynamodbEndpoint,
			String tablePrefix,
			/** S3 bucket for built artifacts. Only used by the 'aws' profile. */
			String artifactBucket,
			/** Public base URL artifacts are served from (CloudFront domain, later). */
			String publicBaseUrl,
			/** Serverless-function (Lambda) settings for Phase 6. */
			Lambda lambda,
			/** Custom-domain (ACM + Route53) settings for Phase 8. */
			Domains domains) {
	}

	public record Domains(
			/**
			 * Region ACM certificates are created in. MUST be {@code us-east-1} for domains
			 * served through CloudFront — ACM certs for CloudFront are only honored from there.
			 */
			String certRegion,
			/**
			 * The CloudFront distribution domain ({@code dxxxx.cloudfront.net}) users point their
			 * custom domain's CNAME at. Informational — surfaced on the domain record. Blank until
			 * a CloudFront distribution exists for this account.
			 */
			String cloudfrontTarget,
			/**
			 * Optional Route53 hosted-zone id. When set, the platform upserts the ACM DNS-validation
			 * CNAME automatically; when blank, the record is returned for the user to add by hand.
			 */
			String route53HostedZoneId) {
	}

	public record Lambda(
			/**
			 * Execution-role ARN every deployed function assumes. Create it once out-of-band
			 * (see README) and paste the ARN here — the app never calls {@code iam:CreateRole}.
			 * When blank, function deployment is skipped and only the static site is published.
			 */
			String roleArn,
			/** Lambda runtime identifier, e.g. {@code nodejs20.x}. */
			String runtime,
			/** Handler entrypoint, e.g. {@code index.handler}. */
			String handler,
			/** Function timeout in seconds. */
			int timeoutSeconds,
			/** Memory allocation in MB. */
			int memoryMb,
			/**
			 * Function URL auth type: {@code NONE} for a public URL (Vercel-style) or
			 * {@code AWS_IAM} to require signed requests.
			 */
			String urlAuthType) {
	}

	public record Build(
			/** Where finished artifacts land in local mode. */
			String localArtifactDir,
			Duration timeout,
			/** Reject artifacts larger than this, to keep a runaway build from filling the disk. */
			long maxArtifactBytes,
			/** Git hosts we are willing to clone from. */
			List<String> allowedGitHosts,
			/** Build commands we are willing to execute. */
			List<String> allowedBuildCommands) {
	}

	public record Webhook(
			/** GitHub webhook secret for HMAC-SHA256 signature verification. */
			String githubSecret) {
	}

	public record Limits(
			/** Maximum number of projects a user can create. */
			int maxProjectsPerUser,
			/** Maximum number of deployments per project per hour. */
			int maxDeploymentsPerHour,
			/** Maximum number of active (queued/building) deployments per project. */
			int maxActiveDeployments) {
	}
}
