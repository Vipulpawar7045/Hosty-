package com.minivercel.function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipInputStream;

import com.minivercel.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.waiters.LambdaWaiter;

/**
 * Unit tests for {@link LambdaFunctionDeployer} against a mocked {@link LambdaClient}.
 * No AWS account involved: the role-ARN is fake and every SDK call is stubbed.
 */
class LambdaFunctionDeployerTest {

	private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/fake-exec";
	private static final String FUNCTION_URL = "https://mv-p1.lambda-url.ap-south-1.on.aws/";

	@TempDir
	Path tmp;

	LambdaClient lambda;
	LambdaWaiter waiter;

	@BeforeEach
	void setUp() {
		lambda = mock(LambdaClient.class);
		waiter = mock(LambdaWaiter.class);
		when(lambda.waiter()).thenReturn(waiter);
	}

	// ── Skipped cases ──────────────────────────────────────────────────────────────

	@Test
	void skipsWhenNoRoleConfigured() throws Exception {
		LambdaFunctionDeployer deployer = new LambdaFunctionDeployer(lambda, props(null, "NONE"));
		Path repo = staticOnlyRepo();

		FunctionDeployResult result = deployer.deploy(repo, "mv-p1", new StringBuilder());

		assertThat(result.deployed()).isFalse();
		assertThat(result.failed()).isFalse();
		assertThat(result.message()).contains("No Lambda role configured");
		verifyNoInteractions(lambda);
	}

	@Test
	void skipsWhenRepoHasNoFunctionDir() throws Exception {
		LambdaFunctionDeployer deployer = new LambdaFunctionDeployer(lambda, props(ROLE_ARN, "NONE"));
		Path repo = staticOnlyRepo();

		FunctionDeployResult result = deployer.deploy(repo, "mv-p1", new StringBuilder());

		assertThat(result.deployed()).isFalse();
		assertThat(result.failed()).isFalse();
		assertThat(result.message()).contains("No api/ or functions/ directory");
		verifyNoInteractions(lambda);
	}

	@Test
	void treatsFailedDeployAsFailureNotSkip() throws Exception {
		when(lambda.getFunction(any(GetFunctionRequest.class)))
				.thenThrow(ResourceNotFoundException.builder().build());
		// Simulate a transient AWS error on create, e.g. InvalidParameterValueException.
		when(lambda.createFunction(any(CreateFunctionRequest.class)))
				.thenThrow(ResourceNotFoundException.builder().message("boom").build());

		LambdaFunctionDeployer deployer = new LambdaFunctionDeployer(lambda, props(ROLE_ARN, "NONE"));
		Path repo = functionRepo("index.js", "exports.handler = () => ({ statusCode: 200 });\n");

		FunctionDeployResult result = deployer.deploy(repo, "mv-p1", new StringBuilder());

		assertThat(result.failed()).isTrue();
		assertThat(result.deployed()).isFalse();
		assertThat(result.message()).contains("boom");
	}

	// ── First deploy (create path) ─────────────────────────────────────────────────

	@Test
	void deploysNewFunctionAndCreatesPublicUrl() throws Exception {
		when(lambda.getFunction(any(GetFunctionRequest.class)))
				.thenThrow(ResourceNotFoundException.builder().build());
		when(lambda.createFunction(any(CreateFunctionRequest.class)))
				.thenReturn(CreateFunctionResponse.builder().build());
		// First deploy: no URL config exists yet, so the get throws and the code creates one.
		when(lambda.getFunctionUrlConfig(any(GetFunctionUrlConfigRequest.class)))
				.thenThrow(ResourceNotFoundException.builder().build());
		when(lambda.createFunctionUrlConfig(any(CreateFunctionUrlConfigRequest.class)))
				.thenReturn(CreateFunctionUrlConfigResponse.builder().functionUrl(FUNCTION_URL).build());
		when(lambda.addPermission(any(AddPermissionRequest.class)))
				.thenReturn(software.amazon.awssdk.services.lambda.model.AddPermissionResponse.builder().build());

		LambdaFunctionDeployer deployer = new LambdaFunctionDeployer(lambda, props(ROLE_ARN, "NONE"));
		Path repo = functionRepo("index.js", "exports.handler = () => ({ statusCode: 200 });\n");

		FunctionDeployResult result = deployer.deploy(repo, "mv-p1", new StringBuilder());

		assertThat(result.deployed()).isTrue();
		assertThat(result.functionName()).isEqualTo("mv-p1");
		assertThat(result.functionUrl()).isEqualTo(FUNCTION_URL);

		// createFunction carries the config and the zipped code.
		var captor = org.mockito.ArgumentCaptor.forClass(CreateFunctionRequest.class);
		verify(lambda).createFunction(captor.capture());
		CreateFunctionRequest req = captor.getValue();
		assertThat(req.functionName()).isEqualTo("mv-p1");
		assertThat(req.runtimeAsString()).isEqualTo("nodejs20.x");
		assertThat(req.role()).isEqualTo(ROLE_ARN);
		assertThat(req.handler()).isEqualTo("index.handler");
		assertThat(req.timeout()).isEqualTo(30);
		assertThat(req.memorySize()).isEqualTo(256);

		// The zip is non-empty and actually contains the handler file.
		byte[] zip = req.code().zipFile().asByteArray();
		assertThat(zip).isNotEmpty();
		assertThat(zipContains(zip, "index.js")).isTrue();

		var urlCaptor = org.mockito.ArgumentCaptor.forClass(CreateFunctionUrlConfigRequest.class);
		verify(lambda).createFunctionUrlConfig(urlCaptor.capture());
		assertThat(urlCaptor.getValue().authTypeAsString()).isEqualTo("NONE");

		// Public invoke permission is granted for the NONE-auth URL.
		var permCaptor = org.mockito.ArgumentCaptor.forClass(AddPermissionRequest.class);
		verify(lambda, org.mockito.Mockito.atLeastOnce()).addPermission(permCaptor.capture());
		AddPermissionRequest perm = permCaptor.getAllValues().get(0);
		assertThat(perm.principal()).isEqualTo("*");
		assertThat(perm.action()).isEqualTo("lambda:InvokeFunctionUrl");
		assertThat(perm.functionName()).isEqualTo("mv-p1");
	}

	// ── Redeploy (update path) ─────────────────────────────────────────────────────

	@Test
	void redeployUpdatesExistingFunctionAndReusesUrl() throws Exception {
		when(lambda.getFunction(any(GetFunctionRequest.class)))
				.thenReturn(GetFunctionResponse.builder().build());
		when(lambda.updateFunctionCode(any(UpdateFunctionCodeRequest.class)))
				.thenReturn(UpdateFunctionCodeResponse.builder().build());
		when(lambda.getFunctionUrlConfig(any(GetFunctionUrlConfigRequest.class)))
				.thenReturn(GetFunctionUrlConfigResponse.builder().functionUrl(FUNCTION_URL).build());
		// Permission already exists on the earlier deploy.
		when(lambda.addPermission(any(AddPermissionRequest.class)))
				.thenThrow(ResourceConflictException.builder().build());

		LambdaFunctionDeployer deployer = new LambdaFunctionDeployer(lambda, props(ROLE_ARN, "NONE"));
		Path repo = functionRepo("index.js", "exports.handler = () => ({ statusCode: 200 });\n");

		FunctionDeployResult result = deployer.deploy(repo, "mv-p1", new StringBuilder());

		assertThat(result.deployed()).isTrue();
		assertThat(result.functionUrl()).isEqualTo(FUNCTION_URL);

		verify(lambda, never()).createFunction(any(CreateFunctionRequest.class));
		verify(lambda).updateFunctionCode(any(UpdateFunctionCodeRequest.class));
		verify(lambda, never()).createFunctionUrlConfig(any(CreateFunctionUrlConfigRequest.class));
		// ResourceConflictException on addPermission is swallowed — permission already there.
		verify(lambda, org.mockito.Mockito.atLeastOnce()).addPermission(any(AddPermissionRequest.class));
	}

	// ── Fixtures ───────────────────────────────────────────────────────────────────

	private Path staticOnlyRepo() throws Exception {
		Path repo = Files.createDirectories(tmp.resolve("static"));
		Files.writeString(repo.resolve("index.html"), "<h1>hi</h1>");
		return repo;
	}

	/** Builds an {@code api/} function dir with one handler and no package.json. */
	private Path functionRepo(String filename, String content) throws Exception {
		Path api = Files.createDirectories(tmp.resolve("api"));
		Files.writeString(api.resolve(filename), content);
		return tmp;
	}

	private static boolean zipContains(byte[] zip, String entryName) throws Exception {
		try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
			var entry = zis.getNextEntry();
			while (entry != null) {
				if (entry.getName().equals(entryName)) {
					return true;
				}
				entry = zis.getNextEntry();
			}
		}
		return false;
	}

	private static AppProperties props(String roleArn, String urlAuthType) {
		return new AppProperties(
				new AppProperties.Jwt("test-secret-test-secret-test-secret-test-secret", Duration.ofHours(1)),
				new AppProperties.Aws("ap-south-1", null, "mv-", "bucket", "https://base.example.com",
						new AppProperties.Lambda(roleArn, "nodejs20.x", "index.handler", 30, 256, urlAuthType),
						new AppProperties.Domains("us-east-1", "", "")),
				new AppProperties.Build("./artifacts", Duration.ofMinutes(10), 104_857_600L,
						List.of("github.com"), List.of("npm run build")),
				new AppProperties.Webhook("secret"),
				new AppProperties.Limits(10, 10, 3));
	}
}
