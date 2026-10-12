package me.inthefield.gitbutlerforjetbrains.core

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ButCommit(
    val cliId: String,
    val commitId: String,
    val message: String,
    val authorName: String,
    val createdAt: String,
    val conflicted: Boolean,
    val changes: List<UncommittedChange> = emptyList(),
)

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class VirtualBranch(
    val cliId: String,
    val name: String,
    val commits: List<ButCommit> = emptyList(),
    val branchStatus: String = "",
)

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ButStack(val cliId: String, val branches: List<VirtualBranch>, val assignedChanges: List<UncommittedChange> = emptyList())

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class UncommittedChange(val cliId: String, val filePath: String, val changeType: String)

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class WorkspaceStatus(
    val uncommittedChanges: List<UncommittedChange>,
    val branches: List<VirtualBranch>,
    val stacks: List<ButStack> = emptyList(),
)

/**
 * `but setup --json` on CLI 0.22.3. [projectStatus] is the serde lowercase enum
 * (`added` or `alreadyexists`). [target] is absent when the CLI did not report one.
 */
@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ButSetupResult(
    val repositoryPath: String = "",
    val projectStatus: String = "",
    val target: ButSetupTarget? = null,
)

/** Target branch recorded by `but setup --json` (`branchName` is like `origin/main` or `gb-local/main`). */
@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ButSetupTarget(
    val branchName: String = "",
    val remoteName: String = "",
    val newlySet: Boolean = false,
)

/** `but teardown --json` on CLI 0.22.3: the oplog snapshot and the branch that was checked out. */
@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ButTeardownResult(
    val snapshotId: String = "",
    val checkedOutBranch: String = "",
)

sealed class ButResult<out T> {
    data class Ok<T>(val value: T) : ButResult<T>()
    data class Err(val message: String) : ButResult<Nothing>()
}
