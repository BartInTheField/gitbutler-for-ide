package me.inthefield.gitbutlerforjetbrains.core

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** Captured output of one `but` invocation. [spawnError] non-null means the process never
 *  ran (binary missing on disk, spawn failure); [timedOut] means it was killed on timeout. */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ProcessResult(
    val exitCode: Int = -1,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
    val spawnError: String? = null,
)

/**
 * The host-specific seam [ButClient] runs on. Each host supplies repository detection,
 * `but` binary resolution, and process execution; all command construction, exit-code
 * handling, JSON parsing and path mapping stay shared in [ButClient].
 */
interface ButEnvironment {
    /** Absolute path to the repository root, or null when the host has no git repo. */
    fun repoRoot(): String?

    /** Absolute path to the resolved `but` binary, or null when it cannot be found. */
    fun executable(): String?

    /** Run `but <args>` in [repoRoot] with the resolved [exe]. Must not throw — surface
     *  failures via [ProcessResult.spawnError] / [ProcessResult.timedOut]. Suspends so the
     *  JS host can spawn asynchronously (no extension-host freeze); the JVM host runs it
     *  blocking on a background dispatcher. */
    suspend fun run(exe: String, repoRoot: String, args: List<String>, timeoutMs: Int): ProcessResult
}
