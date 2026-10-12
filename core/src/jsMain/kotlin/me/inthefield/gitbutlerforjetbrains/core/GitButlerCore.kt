package me.inthefield.gitbutlerforjetbrains.core

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.js.Promise

/**
 * JS entry point for the VSCode extension. Wraps the shared [ButClient] over a
 * [NodeButEnvironment] and returns JSON-string envelopes so TypeScript consumes plain
 * objects (`{ ok: true, value }` / `{ ok: false, error }`) with no Kotlin/JS interop.
 *
 * The CLI operations run `but` asynchronously and are exposed as `Promise<string>`, so
 * awaiting them never blocks the VS Code extension host. Pure/instant helpers stay sync.
 */
@OptIn(DelicateCoroutinesApi::class)
@JsExport
class GitButlerCore(workspacePath: String) {
    private val env = NodeButEnvironment(workspacePath)
    private val client = ButClient(env) { msg -> console.warn(msg) }

    /** Absolute path to the resolved `but` binary, or null. Synchronous (filesystem probe). */
    fun findExecutable(): String? = env.executable()

    /** True iff the resolved repo is checked out on gitbutler/workspace. Synchronous (fast git call). */
    fun isGitButlerWorkspace(): Boolean = env.currentBranch() == ButClient.WORKSPACE_BRANCH

    /** True iff [workspacePath] is inside a Git work tree. Synchronous (fast git call). */
    fun isGitRepository(): Boolean = env.repoRoot() != null

    /**
     * Branch names a leave-mode picker may pass to [teardown], applied virtual branches
     * first. See [ButTeardown.checkoutCandidates].
     */
    fun checkoutCandidates(applied: Array<String>, local: Array<String>): Array<String> =
        ButTeardown.checkoutCandidates(applied.toList(), local.toList()).toTypedArray()

    /** True when bare `but teardown` has an applied branch to check out. See [ButTeardown.hasCheckoutDefault]. */
    fun hasCheckoutDefault(applied: Array<String>): Boolean =
        ButTeardown.hasCheckoutDefault(applied.toList())

    fun statusJson(): Promise<String> = GlobalScope.promise {
        when (val r = client.status()) {
            is ButResult.Ok -> okEnvelope(Json.encodeToJsonElement(r.value))
            is ButResult.Err -> errEnvelope(r.message)
        }
    }

    fun commit(branch: String, message: String, filePaths: Array<String>): Promise<String> = GlobalScope.promise {
        when (val r = client.commit(branch, message, filePaths.toList())) {
            is ButResult.Ok -> okEnvelope(JsonPrimitive(r.value))
            is ButResult.Err -> errEnvelope(r.message)
        }
    }

    fun amend(commitId: String, filePaths: Array<String>): Promise<String> =
        GlobalScope.promise { unitEnvelope(client.amend(commitId, filePaths.toList())) }

    fun push(branch: String): Promise<String> = GlobalScope.promise { unitEnvelope(client.push(branch)) }

    fun pull(): Promise<String> = GlobalScope.promise { unitEnvelope(client.pull()) }

    fun apply(branch: String): Promise<String> = GlobalScope.promise { unitEnvelope(client.apply(branch)) }

    fun unapply(branch: String): Promise<String> = GlobalScope.promise { unitEnvelope(client.unapply(branch)) }

    /**
     * Creates an empty, unstacked virtual branch. Rejects a name git would refuse before
     * spawning `but`; hosts can pre-validate the same way via [ButBranch.newBranchNameError].
     */
    fun newBranch(name: String): Promise<String> = GlobalScope.promise { unitEnvelope(client.newBranch(name)) }

    fun uncommit(id: String): Promise<String> = GlobalScope.promise { unitEnvelope(client.uncommit(id)) }

    fun reword(commitId: String, message: String): Promise<String> =
        GlobalScope.promise { unitEnvelope(client.reword(commitId, message)) }

    /** `but setup --json`. Resolves to `{ ok: true, value: ButSetupResult }` or `{ ok: false, error }`. */
    fun setup(): Promise<String> = GlobalScope.promise {
        when (val r = client.setup()) {
            is ButResult.Ok -> okEnvelope(Json.encodeToJsonElement(r.value))
            is ButResult.Err -> errEnvelope(r.message)
        }
    }

    /**
     * `but teardown --json`. A blank [checkoutTo] omits `--checkout-to` so the CLI checks
     * out the first applied branch. Resolves to `{ ok: true, value: ButTeardownResult }`.
     */
    fun teardown(checkoutTo: String): Promise<String> = GlobalScope.promise {
        when (val r = client.teardown(checkoutTo)) {
            is ButResult.Ok -> okEnvelope(Json.encodeToJsonElement(r.value))
            is ButResult.Err -> errEnvelope(r.message)
        }
    }

    /** Pure, offline helper (no process spawn): parse a `but status --json` payload and
     *  re-serialize the parsed [WorkspaceStatus] — used by tests and for reuse in tooling. */
    fun parseStatusJson(statusJson: String): String =
        Json.encodeToString(WorkspaceStatus.serializer(), ButJsonParser.parseStatus(statusJson))

    private fun okEnvelope(value: JsonElement): String =
        buildJsonObject { put("ok", true); put("value", value) }.toString()

    private fun errEnvelope(message: String): String =
        buildJsonObject { put("ok", false); put("error", message) }.toString()

    private fun unitEnvelope(r: ButResult<Unit>): String = when (r) {
        is ButResult.Ok -> buildJsonObject { put("ok", true) }.toString()
        is ButResult.Err -> errEnvelope(r.message)
    }
}
