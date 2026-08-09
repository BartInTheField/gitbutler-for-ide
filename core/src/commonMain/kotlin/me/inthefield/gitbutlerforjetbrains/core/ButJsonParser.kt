package me.inthefield.gitbutlerforjetbrains.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Pure JSON parsing for the GitButler `but` CLI output. Multiplatform (kotlinx.serialization
 * DOM), no platform APIs, so these functions are directly unit-testable on every target.
 *
 * Malformed / unexpected JSON: [parseStatus] and [parseCommitResult] let the underlying
 * kotlinx.serialization exception propagate. Callers (the client) turn that into an Err.
 * [parseErrorMessage] never throws — it falls back to the raw input.
 */
object ButJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses `but status -f --json`. branches = all branches of all stacks, in order
     * (the same [VirtualBranch] instances that appear under [WorkspaceStatus.stacks]).
     * uncommittedChanges also includes every stack's assignedChanges entries; each stack's
     * assignedChanges are ADDITIONALLY exposed on [ButStack.assignedChanges] (the same
     * [UncommittedChange] instances appear in both the flat list and on the stack).
     *
     * Each branch carries its parsed commits and branchStatus (missing branchStatus -> "").
     * Fields like reviewId, ci, upstreamCommits, mergeBase and upstreamState are ignored.
     * Each commit carries its parsed changes (missing "changes" -> emptyList()); branch-level
     * "changes" fields, if any, are not parsed here.
     */
    fun parseStatus(jsonText: String): WorkspaceStatus {
        val root = json.parseToJsonElement(jsonText).jsonObject

        val uncommitted = mutableListOf<UncommittedChange>()
        (root["uncommittedChanges"] as? JsonArray)?.forEach { element ->
            parseChange(element)?.let { uncommitted.add(it) }
        }

        val branches = mutableListOf<VirtualBranch>()
        val stacks = mutableListOf<ButStack>()
        (root["stacks"] as? JsonArray)?.forEach { stackElement ->
            val stack = stackElement as? JsonObject ?: return@forEach
            val assigned = mutableListOf<UncommittedChange>()
            (stack["assignedChanges"] as? JsonArray)?.forEach { element ->
                parseChange(element)?.let {
                    uncommitted.add(it)
                    assigned.add(it)
                }
            }
            val stackBranches = mutableListOf<VirtualBranch>()
            (stack["branches"] as? JsonArray)?.forEach { branchElement ->
                (branchElement as? JsonObject)?.let { stackBranches.add(parseBranch(it)) }
            }
            branches.addAll(stackBranches)
            stacks.add(ButStack(cliId = stringOrEmpty(stack, "cliId"), branches = stackBranches, assignedChanges = assigned))
        }

        return WorkspaceStatus(uncommittedChanges = uncommitted, branches = branches, stacks = stacks)
    }

    /**
     * Parses `but commit --json` output. Ok(commitId) on success.
     *
     * but 0.22 emits `{commitId, changeId, branch}` on success and signals failure via a
     * non-zero exit code plus a plain-text stderr message (handled by the caller), so this
     * only runs on exit 0. A shape without `commitId` returns Ok("") (commit succeeded,
     * id unknown); the caller logs the raw output. A defensive `error` field still maps to Err.
     */
    fun parseCommitResult(jsonText: String): ButResult<String> {
        val root = json.parseToJsonElement(jsonText).jsonObject

        if (root.containsKey("error")) {
            return ButResult.Err(parseErrorMessage(jsonText))
        }

        return ButResult.Ok(stringOrEmpty(root, "commitId"))
    }

    /** Extracts "message" (fallback "error", fallback raw) from an error JSON. Never throws. */
    fun parseErrorMessage(jsonText: String): String {
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            optString(root, "message") ?: optString(root, "error") ?: jsonText
        } catch (_: Exception) {
            jsonText
        }
    }

    private fun parseChange(element: JsonElement): UncommittedChange? {
        val obj = element as? JsonObject ?: return null
        return UncommittedChange(
            cliId = stringOrEmpty(obj, "cliId"),
            filePath = stringOrEmpty(obj, "filePath"),
            changeType = stringOrEmpty(obj, "changeType"),
        )
    }

    private fun parseBranch(obj: JsonObject): VirtualBranch {
        val commits = mutableListOf<ButCommit>()
        (obj["commits"] as? JsonArray)?.forEach { element ->
            (element as? JsonObject)?.let { commits.add(parseCommit(it)) }
        }
        return VirtualBranch(
            cliId = stringOrEmpty(obj, "cliId"),
            name = stringOrEmpty(obj, "name"),
            commits = commits,
            branchStatus = stringOrEmpty(obj, "branchStatus"),
        )
    }

    private fun parseCommit(obj: JsonObject): ButCommit {
        val changes = mutableListOf<UncommittedChange>()
        (obj["changes"] as? JsonArray)?.forEach { element ->
            parseChange(element)?.let { changes.add(it) }
        }
        return ButCommit(
            cliId = stringOrEmpty(obj, "cliId"),
            commitId = stringOrEmpty(obj, "commitId"),
            message = stringOrEmpty(obj, "message"),
            authorName = stringOrEmpty(obj, "authorName"),
            createdAt = stringOrEmpty(obj, "createdAt"),
            conflicted = optBoolean(obj, "conflicted"),
            changes = changes,
        )
    }

    /** Reads a boolean; null / missing / non-boolean -> false. */
    private fun optBoolean(obj: JsonObject, key: String): Boolean =
        (obj[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun stringOrEmpty(obj: JsonObject, key: String): String = optString(obj, key) ?: ""

    private fun optString(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull
}
