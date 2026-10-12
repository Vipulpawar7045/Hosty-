package com.minivercel.function;

import java.nio.file.Path;

/**
 * Deploys a serverless function found in a built repo to a compute backend and
 * returns a publicly-invokable URL.
 *
 * <p>Phase 6 ships a single {@link LambdaFunctionDeployer} (AWS Lambda + Function URL);
 * the interface exists so a container/Fargate-backed runner can slot in later without
 * touching {@code DeploymentService}.
 */
public interface FunctionDeployer {

	/**
	 * Detects a function directory under {@code repoDir}, packages it, and deploys it
	 * under the given function name.
	 *
	 * <p>The caller owns the naming policy: production deploys pass {@code mv-<projectId>}
	 * while previews pass {@code mv-<projectId>-<branchSlug>}, so a preview never overwrites
	 * the production function. The name is sanitized to Lambda's charset defensively.
	 *
	 * @param repoDir      the cloned repo root (functions live in {@code api/} or {@code functions/})
	 * @param functionName the final Lambda function name to create or update
	 * @param logSink      build log accumulator; the deployer appends its progress here
	 * @return the outcome — never null; check {@link FunctionDeployResult#failed()}
	 */
	FunctionDeployResult deploy(Path repoDir, String functionName, StringBuilder logSink);

	/**
	 * Tears down a previously-deployed function (used when a preview is removed). Deleting the
	 * function also removes its Function URL config and resource policies. A missing function is
	 * treated as already gone — this never throws for that case.
	 *
	 * @param functionName the Lambda function name to delete
	 */
	void remove(String functionName);
}
