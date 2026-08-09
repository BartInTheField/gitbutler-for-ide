package me.inthefield.gitbutlerforjetbrains.core

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Pure commit-field formatting shared by both IDE frontends. Operates on plain strings so the
 * JVM host (real [ButCommit] instances) and the JS host (parsed-JSON objects) call it the same way.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object ButCommits {
    /** The stable id to pass to `but reword`/`but uncommit`/`but amend`: the GitButler change id, falling back to the sha. */
    fun effectiveId(cliId: String, commitId: String): String = cliId.ifBlank { commitId }

    /** Abbreviated commit sha used in tree labels and notifications. */
    fun shortId(commitId: String): String = commitId.take(7)

    /** First line of a commit message — the one-line summary shown in tree rows and prompts. */
    fun summary(message: String): String = message.lineSequence().firstOrNull().orEmpty()
}
