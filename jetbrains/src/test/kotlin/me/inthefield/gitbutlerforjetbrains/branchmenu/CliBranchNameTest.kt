package me.inthefield.gitbutlerforjetbrains.branchmenu

import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.repo.GitRemote
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the branch-name → CLI-argument mapping: remote branches must be passed to
 * `but` by their SHORT name (nameForRemoteOperations), local branches as-is.
 */
class CliBranchNameTest {

    private fun remote(name: String) =
        GitRemote(name, listOf("url"), listOf("pushUrl"), emptyList(), emptyList())

    @Test
    fun localBranch_usesNameAsIs() {
        assertEquals(
            "feature/x",
            GitButlerBranchActionGroup.cliBranchName(GitLocalBranch("feature/x")),
        )
    }

    @Test
    fun remoteBranch_stripsRemotePrefix() {
        val branch = GitStandardRemoteBranch(remote("origin"), "feature/x")
        assertEquals("origin/feature/x", branch.name)
        assertEquals("feature/x", GitButlerBranchActionGroup.cliBranchName(branch))
    }

    @Test
    fun remoteBranch_multiSlashShortNameSurvives() {
        val branch = GitStandardRemoteBranch(remote("upstream"), "feature/ena-5068/auth-debug")
        assertEquals("feature/ena-5068/auth-debug", GitButlerBranchActionGroup.cliBranchName(branch))
    }
}
