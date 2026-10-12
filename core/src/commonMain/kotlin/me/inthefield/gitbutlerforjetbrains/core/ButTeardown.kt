package me.inthefield.gitbutlerforjetbrains.core

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Pure helpers for the leave-GitButler-mode branch picker. Shared so both hosts offer
 * the same `--checkout-to` candidates the 0.22.3 CLI will accept.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object ButTeardown {
    /**
     * Local branches a host may pass to [ButCommands.teardown]. Applied virtual branches
     * come first (they are what bare `but teardown` would check out), then other local
     * branches. Blanks and [ButClient.WORKSPACE_BRANCH] are dropped; duplicates collapse.
     */
    fun checkoutCandidates(appliedNames: List<String>, localNames: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (name in appliedNames + localNames) {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed == ButClient.WORKSPACE_BRANCH) continue
            seen.add(trimmed)
        }
        return seen.toList()
    }

    /**
     * True when bare `but teardown` (no `--checkout-to`) has a branch to land on.
     * On CLI 0.22.3 an empty applied-stack set exits 1 with
     * "Failed to determine checkout target branch".
     */
    fun hasCheckoutDefault(appliedNames: List<String>): Boolean =
        appliedNames.any { name ->
            val trimmed = name.trim()
            trimmed.isNotEmpty() && trimmed != ButClient.WORKSPACE_BRANCH
        }
}
