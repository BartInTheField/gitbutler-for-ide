package me.inthefield.gitbutlerforjetbrains.integration

import me.inthefield.gitbutlerforjetbrains.core.ButCommands
import me.inthefield.gitbutlerforjetbrains.core.ButJsonParser
import me.inthefield.gitbutlerforjetbrains.core.ButPathMapper
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.WorkspaceStatus
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile

/**
 * Integration tests that run the REAL GitButler `but` CLI inside a Docker container
 * against real git repos, capture its `--json` output, and feed it through the
 * plugin's own [ButCommands] (exact argument lists), [ButPathMapper] (path -> cliId)
 * and [ButJsonParser]. Asserts on the parsed [WorkspaceStatus] model — this validates
 * the plugin's CLI contract end-to-end.
 *
 * The CLI version deliberately floats to the latest release (the installer API has no
 * pin mechanism): these tests exist to catch contract drift when GitButler updates.
 * The installed version is printed at container startup for diagnosis. Behavior
 * comments marked "Observed on but 0.22.0" may legitimately change with newer CLIs —
 * a failure there means the plugin's assumptions need re-checking, not a broken test.
 *
 * The whole class is skipped (not failed) when Docker is unavailable.
 */
class ButStatusIntegrationTest {

    companion object {
        // One shared container for the whole class, constructed lazily in @BeforeClass:
        // anything Testcontainers-related that runs during static init throws a
        // java.lang.Error on machines without Docker (e.g. CI), which JUnit reports as a
        // failure instead of honoring the Assume-based skip. The availability check is
        // likewise hardened against Errors (runCatching catches Throwable).
        private lateinit var container: GenericContainer<*>

        @BeforeClass
        @JvmStatic
        fun startContainer() {
            val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }
                .getOrDefault(false)
            assumeTrue("Docker not available — skipping GitButler CLI integration tests", dockerAvailable)

            // Built from a Dockerfile that installs the real `but` CLI. libdbus-1-3 is
            // REQUIRED — the binary fails to load without it.
            container = GenericContainer(
                ImageFromDockerfile()
                    .withDockerfileFromBuilder { builder ->
                        builder
                            .from("debian:bookworm-slim")
                            .run(
                                "apt-get update && " +
                                    "apt-get install -y curl ca-certificates git libdbus-1-3 && " +
                                    "rm -rf /var/lib/apt/lists/*",
                            )
                            .run("curl -fsSL https://gitbutler.com/install.sh | sh")
                            .run(
                                "git config --global user.email \"test@example.com\" && " +
                                    "git config --global user.name \"Test User\" && " +
                                    "git config --global init.defaultBranch main",
                            )
                            .env("PATH", "/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                            .env("BUT_OUTPUT_FORMAT", "json")
                            .cmd("sleep", "infinity")
                            .build()
                    },
            )
            container.start()
            println("GitButler CLI under test: " + container.execInContainer("but", "--version").stdout.trim())
        }

        @AfterClass
        @JvmStatic
        fun stopContainer() {
            if (::container.isInitialized && container.isRunning) {
                container.stop()
            }
        }
    }

    /** Runs a shell command in [workdir]; asserts exit 0 and returns stdout. */
    private fun exec(workdir: String, command: String): String {
        val result = container.execInContainer("sh", "-c", "cd '$workdir' && $command")
        assertEquals(
            "command failed (exit ${result.exitCode}): $command\nstdout:\n${result.stdout}\nstderr:\n${result.stderr}",
            0,
            result.exitCode,
        )
        return result.stdout
    }

    /** Creates a fresh, isolated repo dir bootstrapped with `but setup --init`. */
    private fun freshRepo(name: String): String {
        val dir = "/work/$name"
        exec("/", "rm -rf '$dir' && mkdir -p '$dir'")
        exec(dir, "but setup --init")
        return dir
    }

    /** Runs `but` with the given argument list — shell-quoted, so exactly what the plugin builds. */
    private fun but(repoDir: String, args: List<String>): String =
        exec(repoDir, (listOf("but") + args).joinToString(" ") { shellQuote(it) })

    private fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    /** Runs the plugin's own status command ([ButCommands.status]) and parses it into the plugin model. */
    private fun status(repoDir: String): WorkspaceStatus =
        ButJsonParser.parseStatus(but(repoDir, ButCommands.status()))

    /**
     * Commits every uncommitted change in [repoDir] to [branch] through the plugin's own
     * [ButCommands.commit] — the same path->cliId->commit flow GitButlerService.commit takes.
     */
    private fun commitAll(repoDir: String, branch: String, message: String): String {
        val ids = status(repoDir).uncommittedChanges.map { it.cliId }
        return but(repoDir, ButCommands.commit(branch, message, ids))
    }

    @Test
    fun freshWorkspace_hasNoChangesAndNoStacks() {
        val repo = freshRepo("fresh")

        val s = status(repo)

        // Observed on but 0.21.0: `but setup --init` creates NO default stack.
        assertTrue("expected no uncommitted changes", s.uncommittedChanges.isEmpty())
        assertTrue("expected no stacks", s.stacks.isEmpty())
        assertTrue("expected no branches", s.branches.isEmpty())
    }

    @Test
    fun uncommittedFile_appearsAsSingleUncommittedChange() {
        val repo = freshRepo("uncommitted")
        exec(repo, "echo hi > hello.txt")

        val s = status(repo)

        assertEquals(1, s.uncommittedChanges.size)
        val change = s.uncommittedChanges[0]
        assertTrue("filePath should end with hello.txt: ${change.filePath}", change.filePath.endsWith("hello.txt"))
        assertTrue("cliId should be non-empty", change.cliId.isNotEmpty())
        assertTrue("changeType should be non-empty", change.changeType.isNotEmpty())
    }

    @Test
    fun commitRoundTrip_viaCliIds_producesCommitOnBranch() {
        val repo = freshRepo("roundtrip")
        exec(repo, "but branch new feature-a")
        exec(repo, "echo hi > hello.txt")

        val before = status(repo)
        assertEquals(1, before.uncommittedChanges.size)

        // Resolve the file path to cliIds through the plugin's own mapper — the same
        // path GitButlerService.commit takes with IDE-selected absolute paths.
        val mapped = ButPathMapper.map(repo, listOf("$repo/hello.txt"), before.uncommittedChanges)
        assertTrue("ButPathMapper must not report missing paths: ${mapped.missing}", mapped.missing.isEmpty())
        assertEquals("expected one cliId for hello.txt", 1, mapped.cliIds.size)

        val commitJson = but(repo, ButCommands.commit("feature-a", "add hello", mapped.cliIds))
        val commitResult = ButJsonParser.parseCommitResult(commitJson)
        assertTrue(
            "parseCommitResult must be Ok, got $commitResult (raw: $commitJson)",
            commitResult is ButResult.Ok,
        )
        val parsedCommitId = (commitResult as ButResult.Ok).value
        assertTrue("parseCommitResult must extract the commit id (raw: $commitJson)", parsedCommitId.isNotEmpty())

        val after = status(repo)
        assertTrue("uncommittedChanges should be empty after commit", after.uncommittedChanges.isEmpty())
        assertEquals("expected exactly one stack", 1, after.stacks.size)

        val branch = after.stacks[0].branches.single { it.name == "feature-a" }
        assertEquals("expected exactly one commit on feature-a", 1, branch.commits.size)
        val commit = branch.commits[0]
        assertTrue("commit message should contain 'add hello': ${commit.message}", commit.message.contains("add hello"))
        assertTrue("status commitId should be non-empty", commit.commitId.isNotEmpty())
        // Prefix-tolerant: either command may abbreviate the id in a future CLI version.
        assertTrue(
            "status commitId (${commit.commitId}) must match the id returned by `but commit` ($parsedCommitId)",
            commit.commitId.startsWith(parsedCommitId) || parsedCommitId.startsWith(commit.commitId),
        )
        assertTrue("authorName should be non-empty", commit.authorName.isNotEmpty())
        assertFalse("commit should not be conflicted", commit.conflicted)
        assertTrue("branchStatus should be non-empty", branch.branchStatus.isNotEmpty())
    }

    @Test
    fun uncommit_movesCommitChangeBackToUncommitted() {
        val repo = freshRepo("uncommit")
        exec(repo, "but branch new feature-a")
        exec(repo, "echo hi > hello.txt")

        val before = status(repo)
        val mapped = ButPathMapper.map(repo, listOf("$repo/hello.txt"), before.uncommittedChanges)
        assertTrue("ButPathMapper must not report missing paths: ${mapped.missing}", mapped.missing.isEmpty())
        but(repo, ButCommands.commit("feature-a", "add hello", mapped.cliIds))

        val afterCommit = status(repo)
        val branchBefore = afterCommit.stacks.single().branches.single { it.name == "feature-a" }
        assertEquals("expected exactly one commit before uncommit", 1, branchBefore.commits.size)
        val commitId = branchBefore.commits[0].cliId
        assertTrue("commit cliId should be non-empty", commitId.isNotEmpty())

        but(repo, ButCommands.uncommit(commitId))

        val after = status(repo)
        val branchAfter = after.stacks
            .flatMap { it.branches }
            .singleOrNull { it.name == "feature-a" }
        assertTrue(
            "feature-a should have no commits after uncommit",
            branchAfter == null || branchAfter.commits.isEmpty(),
        )
        assertTrue(
            "hello.txt should be back in uncommittedChanges after uncommit",
            after.uncommittedChanges.any { it.filePath.endsWith("hello.txt") },
        )
    }

    @Test
    fun reword_changesCommitMessage() {
        val repo = freshRepo("reword")
        exec(repo, "but branch new feature-a")
        exec(repo, "echo hi > hello.txt")

        val before = status(repo)
        val mapped = ButPathMapper.map(repo, listOf("$repo/hello.txt"), before.uncommittedChanges)
        assertTrue("ButPathMapper must not report missing paths: ${mapped.missing}", mapped.missing.isEmpty())
        but(repo, ButCommands.commit("feature-a", "original message", mapped.cliIds))

        val afterCommit = status(repo)
        val commit = afterCommit.stacks.single().branches.single { it.name == "feature-a" }.commits.single()
        assertTrue("commit cliId should be non-empty", commit.cliId.isNotEmpty())

        but(repo, ButCommands.reword(commit.cliId, "reworded message"))

        val after = status(repo)
        val reworded = after.stacks.single().branches.single { it.name == "feature-a" }.commits.single()
        assertTrue(
            "commit message should reflect the reword: ${reworded.message}",
            reworded.message.contains("reworded message"),
        )
    }

    @Test
    fun amend_movesSecondChangeIntoExistingCommit_leavesBranchWithOneCommit() {
        val repo = freshRepo("amend")
        exec(repo, "but branch new feature-a")
        exec(repo, "echo hi > hello.txt")

        val before = status(repo)
        val mapped = ButPathMapper.map(repo, listOf("$repo/hello.txt"), before.uncommittedChanges)
        assertTrue("ButPathMapper must not report missing paths: ${mapped.missing}", mapped.missing.isEmpty())
        but(repo, ButCommands.commit("feature-a", "add hello", mapped.cliIds))

        exec(repo, "echo bye > goodbye.txt")
        val withSecondChange = status(repo)
        assertEquals("expected one new uncommitted change", 1, withSecondChange.uncommittedChanges.size)
        val secondMapped = ButPathMapper.map(repo, listOf("$repo/goodbye.txt"), withSecondChange.uncommittedChanges)
        assertTrue(
            "ButPathMapper must not report missing paths: ${secondMapped.missing}",
            secondMapped.missing.isEmpty(),
        )
        val commitId =
            withSecondChange.stacks.single().branches.single { it.name == "feature-a" }.commits.single().cliId

        but(repo, ButCommands.amend(commitId, secondMapped.cliIds))

        val after = status(repo)
        assertTrue(
            "goodbye.txt should have left uncommittedChanges after amend",
            after.uncommittedChanges.none { it.filePath.endsWith("goodbye.txt") },
        )
        val branch = after.stacks.single().branches.single { it.name == "feature-a" }
        assertEquals("feature-a should still have exactly one commit after amend", 1, branch.commits.size)
    }

    @Test
    fun stackedBranches_bothBranchesInOneStack() {
        val repo = freshRepo("stacked")
        exec(repo, "but branch new bottom")
        exec(repo, "echo b > b.txt")
        commitAll(repo, "bottom", "bottom commit")
        exec(repo, "but branch new top --anchor bottom")
        exec(repo, "echo t > t.txt")
        commitAll(repo, "top", "top commit")

        val s = status(repo)

        assertEquals("stacked branches must be ONE stack", 1, s.stacks.size)
        val branches = s.stacks[0].branches
        assertEquals("stack should hold both branches", 2, branches.size)

        // Observed on but 0.22.0: branches are emitted TOP-FIRST (index 0 = top, index 1 = bottom).
        assertEquals("expected top-first ordering", "top", branches[0].name)
        assertEquals("expected bottom second", "bottom", branches[1].name)

        val top = branches.single { it.name == "top" }
        val bottom = branches.single { it.name == "bottom" }
        assertEquals(1, top.commits.size)
        assertEquals(1, bottom.commits.size)
        assertTrue("top commit message: ${top.commits[0].message}", top.commits[0].message.contains("top commit"))
        assertTrue("bottom commit message: ${bottom.commits[0].message}", bottom.commits[0].message.contains("bottom commit"))
    }

    @Test
    fun applyUnapplyRoundTrip_branchLeavesAndRejoinsWorkspace() {
        val repo = freshRepo("applyroundtrip")
        exec(repo, "but branch new feature-x")
        exec(repo, "echo x > x.txt")
        commitAll(repo, "feature-x", "x commit")

        val before = status(repo)
        assertTrue(
            "feature-x must be in the workspace before unapply",
            before.stacks.any { st -> st.branches.any { it.name == "feature-x" } },
        )

        but(repo, ButCommands.unapply("feature-x"))
        val afterUnapply = status(repo)
        assertFalse(
            "feature-x must be gone from the workspace stacks after unapply",
            afterUnapply.stacks.any { st -> st.branches.any { it.name == "feature-x" } },
        )

        but(repo, ButCommands.apply("feature-x"))
        val afterApply = status(repo)
        val branch = afterApply.stacks
            .flatMap { it.branches }
            .singleOrNull { it.name == "feature-x" }
        assertNotNull("feature-x must be back in the workspace after apply", branch)
        assertEquals("feature-x must keep its commit across the round trip", 1, branch!!.commits.size)
        assertTrue(
            "commit message should survive the round trip: ${branch.commits[0].message}",
            branch.commits[0].message.contains("x commit"),
        )
    }

    @Test
    fun applyRemoteOnlyBranch_appearsAsLocalAppliedBranch() {
        val repo = freshRepo("remoteapply")
        val origin = "/work/remoteapply-origin.git"
        exec("/", "rm -rf '$origin' && git init --bare '$origin'")
        exec(repo, "git remote add origin '$origin' && git push origin main")

        // Build the remote-only branch in a scratch clone so it never exists locally
        // in the workspace repo — the plugin applies remote branches by SHORT name.
        val scratch = "/work/remoteapply-scratch"
        exec("/", "rm -rf '$scratch' && git clone '$origin' '$scratch'")
        exec(
            scratch,
            "git checkout -b feature-remote && echo r > r.txt && git add r.txt && " +
                "git commit -m 'remote work' && git push origin feature-remote",
        )
        exec(repo, "git fetch origin")

        but(repo, ButCommands.apply("feature-remote"))

        val s = status(repo)
        val branch = s.stacks
            .flatMap { it.branches }
            .singleOrNull { it.name == "feature-remote" }
        assertNotNull("feature-remote must appear as a local applied branch", branch)
        assertTrue(
            "applied branch should carry the remote commit: ${branch!!.commits.map { it.message }}",
            branch.commits.any { it.message.contains("remote work") },
        )
    }

    @Test
    fun parallelStacks_uncommittedChangeStaysUnassigned() {
        val repo = freshRepo("parallel")
        exec(repo, "but branch new alpha")
        exec(repo, "but branch new beta")

        val twoStacks = status(repo)
        assertEquals("two independent branches must be TWO stacks", 2, twoStacks.stacks.size)

        exec(repo, "echo a > a.txt")

        val s = status(repo)

        // but 0.22 removed CLI change assignment: every worktree change is uncommitted and
        // belongs to no stack, so it appears in the flat list with no stack claiming it.
        assertTrue(
            "a.txt must appear in flat uncommittedChanges",
            s.uncommittedChanges.any { it.filePath.endsWith("a.txt") },
        )
        assertTrue(
            "no stack may report assigned changes now that CLI staging is gone",
            s.stacks.all { it.assignedChanges.isEmpty() },
        )
    }

    @Test
    fun pull_onFreshWorkspace_succeedsAndReportsUpToDate() {
        val repo = freshRepo("pull")

        // `but setup --init` wires a local `gb-local` remote, so pull needs no network and
        // is up to date. Exercised through the plugin's own ButCommands.pull(); exec() asserts
        // exit 0, so this validates the pull command contract end-to-end.
        val out = but(repo, ButCommands.pull())
        assertTrue("pull output should report up_to_date: $out", out.contains("up_to_date"))
    }
}
