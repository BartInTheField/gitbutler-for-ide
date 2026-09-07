package me.inthefield.gitbutlerforjetbrains.core

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Builds the exact `but` argument lists the plugin runs. Pure Kotlin — shared with the
 * integration tests so they execute the same commands the plugin does.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object ButCommands {
    fun status(): List<String> = listOf("status", "-f", "--json")

    fun push(branchName: String): List<String> = listOf("push", branchName, "--json")

    fun pull(): List<String> = listOf("pull", "--json")

    fun apply(branchName: String): List<String> = listOf("apply", branchName, "--json")

    fun unapply(branchName: String): List<String> = listOf("unapply", branchName, "--json")

    /** `but branch new <name>` — creates an empty, unstacked virtual branch in the workspace. */
    fun branchNew(name: String): List<String> = listOf("branch", "new", name, "--json")

    /** [cliIds] are the changes to commit, passed positionally; [branchName] is the target branch. */
    fun commit(branchName: String, message: String, cliIds: List<String>): List<String> =
        listOf("commit", "-b", branchName, "-m", message, "--json") + cliIds

    /** [cliIds] are the uncommitted changes to fold into [commitId], passed positionally. */
    fun amend(commitId: String, cliIds: List<String>): List<String> =
        listOf("amend", "-t", commitId, "--json") + cliIds

    /** [id] is a commit id or a file-in-commit id (e.g. `tp:x`) to uncommit a single file. */
    fun uncommit(id: String): List<String> = listOf("uncommit", id, "--json")

    fun reword(commitId: String, message: String): List<String> =
        listOf("reword", commitId, "-m", message, "--json")
}
