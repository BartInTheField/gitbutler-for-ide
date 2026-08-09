package me.inthefield.gitbutlerforjetbrains.core

/**
 * Host-agnostic GitButler CLI orchestration. Every operation is the same recipe:
 * resolve repo + binary, run the [ButCommands] arg list through the [ButEnvironment],
 * turn a non-zero exit into an Err via [ButJsonParser.parseErrorMessage], then parse.
 * `commit`/`amend` additionally run `status` first and map file paths to change ids.
 *
 * Threading/async is the host's problem: [ButEnvironment.run] blocks, and the JVM host
 * calls these off the EDT while the JS host calls them off the extension's hot path.
 */
class ButClient(
    private val env: ButEnvironment,
    private val logWarn: (String) -> Unit = {},
) {
    suspend fun status(): ButResult<WorkspaceStatus> {
        val output = exec(ButCommands.status()) { return it }
        if (output.exitCode != 0) return errFrom(output)
        return try {
            ButResult.Ok(ButJsonParser.parseStatus(output.stdout))
        } catch (e: Exception) {
            ButResult.Err("Failed to parse `but status` output: ${e.message}")
        }
    }

    suspend fun push(branchName: String): ButResult<Unit> = simpleMutation(ButCommands.push(branchName), PUSH_TIMEOUT_MS)

    suspend fun pull(): ButResult<Unit> = simpleMutation(ButCommands.pull(), PUSH_TIMEOUT_MS)

    suspend fun apply(branchName: String): ButResult<Unit> = simpleMutation(ButCommands.apply(branchName))

    suspend fun unapply(branchName: String): ButResult<Unit> = simpleMutation(ButCommands.unapply(branchName))

    suspend fun commit(branchName: String, message: String, filePaths: List<String>): ButResult<String> {
        if (filePaths.isEmpty()) return ButResult.Err("No files selected to commit")

        val ids = resolveIds(filePaths) { return it }

        val output = exec(ButCommands.commit(branchName, message, ids)) { return it }
        if (output.exitCode != 0) return errFrom(output)

        // Exit 0 means the commit succeeded; the JSON is only enrichment (the commitId),
        // so a parse failure must not surface as a commit failure.
        return try {
            val parsed = ButJsonParser.parseCommitResult(output.stdout)
            if (parsed is ButResult.Ok && parsed.value.isBlank()) {
                logWarn("`but commit` exited 0 but output had no commitId. Raw output: ${output.stdout}")
            }
            parsed
        } catch (e: Exception) {
            logWarn("`but commit` exited 0 but output was unparseable: ${e.message}. Raw output: ${output.stdout}")
            ButResult.Ok("")
        }
    }

    suspend fun amend(commitId: String, filePaths: List<String>): ButResult<Unit> {
        if (filePaths.isEmpty()) return ButResult.Err("No files selected to amend")
        val ids = resolveIds(filePaths) { return it }
        return simpleMutation(ButCommands.amend(commitId, ids))
    }

    suspend fun uncommit(id: String): ButResult<Unit> = simpleMutation(ButCommands.uncommit(id))

    suspend fun reword(commitId: String, message: String): ButResult<Unit> {
        if (message.isBlank()) return ButResult.Err("Commit message must not be blank")
        return simpleMutation(ButCommands.reword(commitId, message))
    }

    // --- shared plumbing -------------------------------------------------------------

    private suspend inline fun exec(args: List<String>, timeoutMs: Int = PROCESS_TIMEOUT_MS, onErr: (ButResult.Err) -> Nothing): ProcessResult {
        val repoRoot = env.repoRoot() ?: onErr(ButResult.Err(NO_REPO_MESSAGE))
        val exe = env.executable() ?: onErr(ButResult.Err(BINARY_MISSING_MESSAGE))
        val result = env.run(exe, repoRoot, args, timeoutMs)
        if (result.spawnError != null) onErr(ButResult.Err("Failed to run GitButler CLI: ${result.spawnError}"))
        if (result.timedOut) onErr(ButResult.Err("`but ${args.firstOrNull().orEmpty()}` timed out after ${timeoutMs / 1000}s"))
        return result
    }

    private suspend fun simpleMutation(args: List<String>, timeoutMs: Int = PROCESS_TIMEOUT_MS): ButResult<Unit> {
        val output = exec(args, timeoutMs) { return it }
        if (output.exitCode != 0) return errFrom(output)
        return ButResult.Ok(Unit)
    }

    private suspend inline fun resolveIds(filePaths: List<String>, onErr: (ButResult.Err) -> Nothing): List<String> {
        val workspace = when (val s = status()) {
            is ButResult.Ok -> s.value
            is ButResult.Err -> onErr(s)
        }
        val repoRoot = env.repoRoot() ?: onErr(ButResult.Err(NO_REPO_MESSAGE))
        val mapped = ButPathMapper.map(repoRoot, filePaths, workspace.uncommittedChanges)
        if (mapped.missing.isNotEmpty()) {
            onErr(ButResult.Err("No uncommitted change found for: ${mapped.missing.joinToString(", ")}"))
        }
        return mapped.cliIds
    }

    private fun errFrom(output: ProcessResult): ButResult.Err =
        ButResult.Err(ButJsonParser.parseErrorMessage(output.stdout.ifBlank { output.stderr }))

    companion object {
        const val WORKSPACE_BRANCH = "gitbutler/workspace"
        const val PROCESS_TIMEOUT_MS = 30_000
        const val PUSH_TIMEOUT_MS = 120_000
        const val NO_REPO_MESSAGE = "No git repository found for this project"
        const val BINARY_MISSING_MESSAGE =
            "GitButler CLI (`but`) not found on PATH (checked PATHEXT extensions on Windows) " +
                "or in ~/.local/bin, /opt/homebrew/bin, /usr/local/bin"
    }
}
