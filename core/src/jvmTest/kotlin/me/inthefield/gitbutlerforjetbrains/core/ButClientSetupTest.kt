package me.inthefield.gitbutlerforjetbrains.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exit-code contract for `but setup` / `but teardown`: a non-zero exit surfaces stderr,
 * and exit 0 is success even when stdout is not the JSON shape (CLI 0.22.3 prints JSON
 * only when `--json` is honoured; a human splash must not fail a setup that worked).
 */
class ButClientSetupTest {

    @Test
    fun setup_parsesJsonOnSuccess() = runBlocking {
        val env = FakeEnv(
            ProcessResult(
                exitCode = 0,
                stdout = """{"repositoryPath":"/repo","projectStatus":"added","target":{"branchName":"gb-local/main","remoteName":"gb-local","newlySet":true}}""",
            ),
        )

        val result = ButClient(env).setup()

        assertEquals(listOf("setup", "--json"), env.calls.single())
        assertTrue(result is ButResult.Ok)
        assertEquals("added", (result as ButResult.Ok).value.projectStatus)
        assertEquals("gb-local/main", result.value.target?.branchName)
    }

    @Test
    fun setup_exitZeroUnparseable_isSuccess() = runBlocking {
        val env = FakeEnv(ProcessResult(exitCode = 0, stdout = "Setting up GitButler project...\n"))

        val result = ButClient(env).setup()

        assertTrue(result is ButResult.Ok)
        assertEquals("", (result as ButResult.Ok).value.repositoryPath)
    }

    @Test
    fun setup_nonzeroExit_surfacesStderr() = runBlocking {
        val env = FakeEnv(ProcessResult(exitCode = 1, stderr = "No git repository found"))

        val result = ButClient(env).setup()

        assertTrue(result is ButResult.Err)
        assertEquals("No git repository found", (result as ButResult.Err).message)
    }

    @Test
    fun teardown_passesCheckoutToAndParsesBranch() = runBlocking {
        val env = FakeEnv(
            ProcessResult(exitCode = 0, stdout = """{"snapshotId":"snap","checkedOutBranch":"main"}"""),
        )

        val result = ButClient(env).teardown("main")

        assertEquals(listOf("teardown", "--checkout-to", "main", "--json"), env.calls.single())
        assertTrue(result is ButResult.Ok)
        assertEquals("main", (result as ButResult.Ok).value.checkedOutBranch)
        assertEquals("snap", result.value.snapshotId)
    }

    @Test
    fun teardown_blankCheckoutTo_omitsFlag() = runBlocking {
        val env = FakeEnv(ProcessResult(exitCode = 0, stdout = """{"snapshotId":"snap","checkedOutBranch":"feature"}"""))

        val result = ButClient(env).teardown("  ")

        assertEquals(listOf("teardown", "--json"), env.calls.single())
        assertTrue(result is ButResult.Ok)
        assertEquals("feature", (result as ButResult.Ok).value.checkedOutBranch)
    }

    @Test
    fun teardown_nonzeroExit_surfacesStderr() = runBlocking {
        val env = FakeEnv(
            ProcessResult(
                exitCode = 1,
                stderr = "Failed to determine checkout target branch. Specify a target branch with `--checkout-to <branch>`.",
            ),
        )

        val result = ButClient(env).teardown(null)

        assertTrue(result is ButResult.Err)
        assertTrue((result as ButResult.Err).message.contains("Failed to determine checkout target branch"))
    }

    private class FakeEnv(private val result: ProcessResult) : ButEnvironment {
        val calls = mutableListOf<List<String>>()

        override fun repoRoot(): String = "/repo"

        override fun executable(): String = "/bin/but"

        override suspend fun run(exe: String, repoRoot: String, args: List<String>, timeoutMs: Int): ProcessResult {
            calls += args
            return result
        }
    }
}
