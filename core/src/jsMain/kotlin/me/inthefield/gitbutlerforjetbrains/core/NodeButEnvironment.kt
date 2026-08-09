package me.inthefield.gitbutlerforjetbrains.core

import kotlinx.coroutines.suspendCancellableCoroutine

private val childProcess: dynamic = js("require('child_process')")
private val nodeOs: dynamic = js("require('os')")
private val nodeFsModule: dynamic = js("require('fs')")

/**
 * Node.js [ButEnvironment]: repo detection via a quick synchronous `git rev-parse`, `but`
 * resolution via the shared [ButExecutableResolver] over the real filesystem, and `but`
 * process execution via the ASYNCHRONOUS `child_process.execFile` bridged into a suspend
 * function — so the VSCode extension host never blocks while `but pull`/`push` run.
 */
internal class NodeButEnvironment(private val workspacePath: String) : ButEnvironment {

    override fun repoRoot(): String? {
        val r = runSync("git", listOf("rev-parse", "--show-toplevel"), workspacePath, 10_000)
        if (r.spawnError != null || r.exitCode != 0) return null
        return r.stdout.trim().ifBlank { null }
    }

    override fun executable(): String? {
        val platform = js("process.platform") as String
        val osName = if (platform == "win32") "windows" else "linux"
        val path = js("process.env.PATH") as? String
        val pathExt = js("process.env.PATHEXT") as? String
        val home = try { nodeOs.homedir() as? String } catch (_: Throwable) { null }
        return ButExecutableResolver.resolve(osName, path, pathExt, home) { candidate ->
            try {
                (nodeFsModule.statSync(candidate).isFile() as Boolean)
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun run(exe: String, repoRoot: String, args: List<String>, timeoutMs: Int): ProcessResult =
        suspendCancellableCoroutine { cont ->
            val options: dynamic = js("({})")
            options.cwd = repoRoot
            options.timeout = timeoutMs
            options.encoding = "utf8"
            options.maxBuffer = 10 * 1024 * 1024
            val env: dynamic = js("Object.assign({}, process.env)")
            env.BUT_OUTPUT_FORMAT = "json"
            options.env = env
            childProcess.execFile(exe, args.toTypedArray(), options) { error, stdout, stderr ->
                val result = if (error == null) {
                    ProcessResult(exitCode = 0, stdout = asStr(stdout), stderr = asStr(stderr))
                } else {
                    val d = error.asDynamic()
                    val timedOut = (d.killed == true) || (d.signal == "SIGTERM")
                    when {
                        timedOut -> ProcessResult(timedOut = true, stdout = asStr(stdout), stderr = asStr(stderr))
                        jsTypeOf(d.code) == "number" -> ProcessResult(exitCode = d.code as Int, stdout = asStr(stdout), stderr = asStr(stderr))
                        else -> ProcessResult(spawnError = if (d.message != null && d.message != undefined) d.message.toString() else "unknown error")
                    }
                }
                cont.resumeWith(Result.success(result))
                Unit
            }
        }

    /** Current checked-out branch of the resolved repo, or null — used for workspace detection. */
    fun currentBranch(): String? {
        val root = repoRoot() ?: return null
        val r = runSync("git", listOf("rev-parse", "--abbrev-ref", "HEAD"), root, 10_000)
        if (r.spawnError != null || r.exitCode != 0) return null
        return r.stdout.trim().ifBlank { null }
    }

    /** Synchronous spawn for the fast, local git-detection calls only (never for `but`). */
    private fun runSync(command: String, args: List<String>, cwd: String, timeoutMs: Int): ProcessResult {
        val options: dynamic = js("({})")
        options.cwd = cwd
        options.timeout = timeoutMs
        options.encoding = "utf8"
        options.maxBuffer = 10 * 1024 * 1024
        return try {
            val stdout = childProcess.execFileSync(command, args.toTypedArray(), options)
            ProcessResult(exitCode = 0, stdout = asStr(stdout), stderr = "")
        } catch (e: Throwable) {
            val d = e.asDynamic()
            when {
                (d.killed == true) || (d.signal == "SIGTERM") -> ProcessResult(timedOut = true)
                jsTypeOf(d.status) == "number" -> ProcessResult(exitCode = d.status as Int, stdout = asStr(d.stdout), stderr = asStr(d.stderr))
                else -> ProcessResult(spawnError = if (d.message != null && d.message != undefined) d.message.toString() else "unknown error")
            }
        }
    }
}

private fun asStr(v: dynamic): String = if (v != null && v != undefined) v.toString() else ""
