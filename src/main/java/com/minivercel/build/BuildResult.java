package com.minivercel.build;

import java.nio.file.Path;

/**
 * What comes back from a {@link BuildRunner} after it finishes (successfully or not).
 *
 * @param success      true when the build command exited with code 0
 * @param artifactDir  the directory containing built files — valid only when {@code success} is true
 * @param repoDir      the cloned repo root — used to detect serverless functions ({@code api/},
 *                     {@code functions/}); may be null for intermediate step results
 * @param logs         combined stdout + stderr captured during the build
 * @param errorMessage human-readable failure reason; null when successful
 */
public record BuildResult(
		boolean success,
		Path artifactDir,
		Path repoDir,
		String logs,
		String errorMessage) {

	public static BuildResult failure(String logs, String errorMessage) {
		return new BuildResult(false, null, null, logs, errorMessage);
	}

	public static BuildResult success(Path artifactDir, String logs) {
		return new BuildResult(true, artifactDir, null, logs, null);
	}

	public static BuildResult success(Path artifactDir, Path repoDir, String logs) {
		return new BuildResult(true, artifactDir, repoDir, logs, null);
	}
}
