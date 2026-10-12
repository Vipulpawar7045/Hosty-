package com.minivercel.build;

/**
 * Everything a {@link BuildRunner} needs to reproduce a single deployment.
 *
 * @param repoUrl      the Git remote to clone (https only in Phase 2)
 * @param buildCommand the shell command to run inside the cloned repo, e.g. {@code npm run build}
 * @param outputDir    relative path inside the repo where artifacts land, e.g. {@code dist}
 * @param branch       optional; when non-null the runner clones this branch (needed so a preview
 *                     build fetches the branch's code, not the default branch's)
 * @param commitSha    optional; when non-null the runner checks out this exact commit
 */
public record BuildRequest(
		String repoUrl,
		String buildCommand,
		String outputDir,
		String branch,
		String commitSha) {
}
