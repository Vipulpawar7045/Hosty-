package com.minivercel.build;

import java.nio.file.Path;

/**
 * Turns a Git repo into a directory of built files.
 *
 * <p>Phase 2 runs builds on this machine. Phase 3 swaps in a CodeBuild-backed implementation
 * without the calling code changing.
 */
public interface BuildRunner {

	/**
	 * Clones and builds a repository.
	 *
	 * @param workDir a scratch directory the runner owns for the duration of the build
	 * @return where the built artifact landed, plus the captured logs
	 */
	BuildResult run(BuildRequest request, Path workDir);
}
