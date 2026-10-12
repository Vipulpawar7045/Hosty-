package com.minivercel.function;

/**
 * Outcome of attempting to deploy a serverless function for a deployment.
 *
 * <p>Three distinct states:
 * <ul>
 *   <li><b>deployed</b> — a function dir was found and pushed to Lambda; {@code functionUrl} is live.</li>
 *   <li><b>skipped</b> — no function dir (or no role configured); this is a normal static-only deploy.</li>
 *   <li><b>failed</b> — a function dir existed but deploying it errored; the caller should surface this.</li>
 * </ul>
 *
 * @param deployed     true only when a function was actually deployed to Lambda
 * @param failed       true when a function was present but deployment errored
 * @param functionName the AWS Lambda function name (null unless deployed)
 * @param functionUrl  the public Function URL (null unless deployed)
 * @param message      human-readable explanation, always set
 */
public record FunctionDeployResult(
		boolean deployed,
		boolean failed,
		String functionName,
		String functionUrl,
		String message) {

	public static FunctionDeployResult deployed(String functionName, String functionUrl) {
		return new FunctionDeployResult(true, false, functionName, functionUrl, "Function deployed");
	}

	public static FunctionDeployResult skipped(String message) {
		return new FunctionDeployResult(false, false, null, null, message);
	}

	public static FunctionDeployResult failed(String message) {
		return new FunctionDeployResult(false, true, null, null, message);
	}
}
