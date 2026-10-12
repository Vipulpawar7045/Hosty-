package com.minivercel.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.minivercel.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Uploads a local artifact directory to S3 under a key prefix.
 *
 * <p>Each file is stored at {@code {projectId}/{deploymentId}/{relPath}} so multiple
 * deployments of the same project coexist and rollback is just a pointer swap.
 */
@Component
public class S3Publisher {

	private static final Logger log = LoggerFactory.getLogger(S3Publisher.class);

	/** Coarse content-type map; good enough for static sites. */
	private static final Map<String, String> CONTENT_TYPES = Map.of(
			"html", "text/html; charset=utf-8",
			"css",  "text/css",
			"js",   "application/javascript",
			"json", "application/json",
			"png",  "image/png",
			"jpg",  "image/jpeg",
			"jpeg", "image/jpeg",
			"svg",  "image/svg+xml",
			"ico",  "image/x-icon");

	private final S3Client s3;
	private final AppProperties props;

	public S3Publisher(S3Client s3, AppProperties props) {
		this.s3 = s3;
		this.props = props;
	}

	/**
	 * Walks {@code artifactDir} and puts every file into S3.
	 *
	 * @return the S3 key prefix that was written (e.g. {@code projectId/deploymentId})
	 */
	public String publish(Path artifactDir, String projectId, String deploymentId)
			throws IOException {

		String bucket = props.aws().artifactBucket();
		String prefix = projectId + "/" + deploymentId;

		try (var stream = Files.walk(artifactDir)) {
			stream.filter(Files::isRegularFile).forEach(file -> {
				String relPath = artifactDir.relativize(file).toString().replace("\\", "/");
				String key = prefix + "/" + relPath;
				String contentType = contentTypeFor(relPath);

				log.debug("Uploading s3://{}/{}", bucket, key);
				s3.putObject(
						PutObjectRequest.builder()
								.bucket(bucket)
								.key(key)
								.contentType(contentType)
								.build(),
						RequestBody.fromFile(file));
			});
		}

		log.info("Published {} artifacts to s3://{}/{}", artifactDir, bucket, prefix);
		return prefix;
	}

	private static String contentTypeFor(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0) return "application/octet-stream";
		String ext = filename.substring(dot + 1).toLowerCase();
		return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
	}

	/**
	 * Deletes every object under {@code prefix} (used to tear down a preview deployment).
	 *
	 * <p>S3 has no "delete folder" call, so we list keys under the prefix and batch-delete
	 * them (up to 1000 per request). Missing keys are a no-op, making this safe to retry.
	 *
	 * @param prefix the key prefix to remove, e.g. {@code projectId/deploymentId}
	 */
	public void deletePrefix(String prefix) {
		String bucket = props.aws().artifactBucket();
		String continuationToken = null;
		int deleted = 0;

		do {
			var listResponse = s3.listObjectsV2(ListObjectsV2Request.builder()
					.bucket(bucket)
					.prefix(prefix + "/")
					.continuationToken(continuationToken)
					.build());

			List<ObjectIdentifier> toDelete = new ArrayList<>();
			for (var obj : listResponse.contents()) {
				toDelete.add(ObjectIdentifier.builder().key(obj.key()).build());
			}

			if (!toDelete.isEmpty()) {
				s3.deleteObjects(DeleteObjectsRequest.builder()
						.bucket(bucket)
						.delete(Delete.builder().objects(toDelete).build())
						.build());
				deleted += toDelete.size();
			}

			continuationToken = Boolean.TRUE.equals(listResponse.isTruncated())
					? listResponse.nextContinuationToken()
					: null;
		} while (continuationToken != null);

		log.info("Deleted {} object(s) under s3://{}/{}", deleted, bucket, prefix);
	}

	/**
	 * Copies all objects under {@code sourcePrefix} to {@code destinationPrefix} within the artifact bucket.
	 * Used for instant rollback without rebuilding.
	 *
	 * @param sourcePrefix source prefix, e.g. {@code projectId/targetDeploymentId}
	 * @param destinationPrefix destination prefix, e.g. {@code projectId/newDeploymentId}
	 * @return number of objects copied
	 */
	public int copyPrefix(String sourcePrefix, String destinationPrefix) {
		String bucket = props.aws().artifactBucket();
		String continuationToken = null;
		int copied = 0;

		do {
			var listResponse = s3.listObjectsV2(ListObjectsV2Request.builder()
					.bucket(bucket)
					.prefix(sourcePrefix + "/")
					.continuationToken(continuationToken)
					.build());

			for (var obj : listResponse.contents()) {
				String sourceKey = obj.key();
				String relPath = sourceKey.substring((sourcePrefix + "/").length());
				String destKey = destinationPrefix + "/" + relPath;

				s3.copyObject(CopyObjectRequest.builder()
						.sourceBucket(bucket)
						.sourceKey(sourceKey)
						.destinationBucket(bucket)
						.destinationKey(destKey)
						.build());
				copied++;
			}

			continuationToken = Boolean.TRUE.equals(listResponse.isTruncated())
					? listResponse.nextContinuationToken()
					: null;
		} while (continuationToken != null);

		log.info("Copied {} object(s) from s3://{}/{} to s3://{}/{}", copied, bucket, sourcePrefix, bucket, destinationPrefix);
		return copied;
	}
}
