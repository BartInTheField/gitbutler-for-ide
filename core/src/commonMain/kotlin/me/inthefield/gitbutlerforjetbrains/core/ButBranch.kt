package me.inthefield.gitbutlerforjetbrains.core

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object ButBranch {
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
}