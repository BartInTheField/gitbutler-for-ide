package me.inthefield.gitbutlerforjetbrains.core

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object ButBranch {

    /** Characters git rejects anywhere in a ref name. */
    private const val ILLEGAL_CHARS = "~^:?*[\\"

    fun statusSuffix(branchStatus: String): String = when (branchStatus) {
        "" -> ""
        "nothingToPush", "nothingToCommit", "clean" -> "✓ pushed"
        "unpushedCommits", "completelyUnpushed", "hasUnpushedCommits" -> "unpushed"
        "ahead", "aheadOfUpstream" -> "ahead"
        "behind", "behindUpstream" -> "behind"
        "diverged", "divergedFromUpstream" -> "diverged"
        "conflicted", "hasConflicts" -> "conflicted"
        "upToDate", "fullySynced" -> "up to date"
        else -> branchStatus.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    }

    /**
     * Validates a user-typed name for a new virtual branch against git's ref-name rules —
     * the same ones `but branch new` and `but commit -b` enforce — so the UI can reject it
     * inline instead of spawning the CLI just to get "Invalid branch name" back.
     *
     * Returns a message to show the user, or null when the name is usable. Whether the name
     * is already taken is deliberately NOT checked here: `but commit -b` targets an existing
     * branch by design, and only the CLI knows the real branch set.
     */
    fun newBranchNameError(name: String): String? = when {
        name.isEmpty() -> "Branch name must not be empty"
        name.any { it.isWhitespace() } -> "Branch name must not contain whitespace"
        name.any { it.code < 0x20 || it.code == 0x7f } -> "Branch name must not contain control characters"
        name.any { it in ILLEGAL_CHARS } ->
            "Branch name must not contain any of ${ILLEGAL_CHARS.toCharArray().joinToString(" ")}"
        name.contains("..") -> "Branch name must not contain '..'"
        name.contains("@{") -> "Branch name must not contain '@{'"
        name == "@" -> "'@' is not a valid branch name"
        name.startsWith("-") -> "Branch name must not start with '-'"
        name.startsWith("/") || name.endsWith("/") || name.contains("//") ->
            "Branch name must not start or end with '/' or contain '//'"
        name.endsWith(".") -> "Branch name must not end with '.'"
        name.split('/').any { it.startsWith(".") } -> "No part of a branch name may start with '.'"
        name.split('/').any { it.endsWith(".lock") } -> "No part of a branch name may end with '.lock'"
        name == ButClient.WORKSPACE_BRANCH -> "'$name' is GitButler's own workspace branch"
        else -> null
    }
}
