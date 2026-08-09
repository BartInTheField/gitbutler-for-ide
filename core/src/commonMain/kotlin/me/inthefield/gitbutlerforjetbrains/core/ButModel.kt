package me.inthefield.gitbutlerforjetbrains.core

import kotlinx.serialization.Serializable

@Serializable
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
data class VirtualBranch(
    val cliId: String,
    val name: String,
    val commits: List<ButCommit> = emptyList(),
    val branchStatus: String = "",
)

@Serializable
data class ButStack(val cliId: String, val branches: List<VirtualBranch>, val assignedChanges: List<UncommittedChange> = emptyList())

@Serializable
data class UncommittedChange(val cliId: String, val filePath: String, val changeType: String)

@Serializable
data class WorkspaceStatus(
    val uncommittedChanges: List<UncommittedChange>,
    val branches: List<VirtualBranch>,
    val stacks: List<ButStack> = emptyList(),
)

sealed class ButResult<out T> {
    data class Ok<T>(val value: T) : ButResult<T>()
    data class Err(val message: String) : ButResult<Nothing>()
}
