package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ButBranchTest {

    @Test
    fun emptyStatus_hasNoSuffix() {
        assertEquals("", ButBranch.statusSuffix(""))
    }

    @Test
    fun pushedSynonyms_mapToPushed() {
        assertEquals("✓ pushed", ButBranch.statusSuffix("nothingToPush"))
        assertEquals("✓ pushed", ButBranch.statusSuffix("nothingToCommit"))
        assertEquals("✓ pushed", ButBranch.statusSuffix("clean"))
    }

    @Test
    fun unpushedSynonyms_mapToUnpushed() {
        assertEquals("unpushed", ButBranch.statusSuffix("unpushedCommits"))
        assertEquals("unpushed", ButBranch.statusSuffix("completelyUnpushed"))
        assertEquals("unpushed", ButBranch.statusSuffix("hasUnpushedCommits"))
    }

    @Test
    fun aheadSynonyms_mapToAhead() {
        assertEquals("ahead", ButBranch.statusSuffix("ahead"))
        assertEquals("ahead", ButBranch.statusSuffix("aheadOfUpstream"))
    }

    @Test
    fun behindSynonyms_mapToBehind() {
        assertEquals("behind", ButBranch.statusSuffix("behind"))
        assertEquals("behind", ButBranch.statusSuffix("behindUpstream"))
    }

    @Test
    fun divergedSynonyms_mapToDiverged() {
        assertEquals("diverged", ButBranch.statusSuffix("diverged"))
        assertEquals("diverged", ButBranch.statusSuffix("divergedFromUpstream"))
    }

    @Test
    fun conflictedSynonyms_mapToConflicted() {
        assertEquals("conflicted", ButBranch.statusSuffix("conflicted"))
        assertEquals("conflicted", ButBranch.statusSuffix("hasConflicts"))
    }

    @Test
    fun upToDateSynonyms_mapToUpToDate() {
        assertEquals("up to date", ButBranch.statusSuffix("upToDate"))
        assertEquals("up to date", ButBranch.statusSuffix("fullySynced"))
    }

    @Test
    fun unknownCamelCase_splitsIntoWords() {
        assertEquals("some Weird State", ButBranch.statusSuffix("someWeirdState"))
        assertEquals("integration Test123 Value", ButBranch.statusSuffix("integrationTest123Value"))
    }

    @Test
    fun unknownSingleWord_passesThroughUnchanged() {
        assertEquals("pending", ButBranch.statusSuffix("pending"))
    }

    // --- newBranchNameError -------------------------------------------------

    @Test
    fun plainNames_areAccepted() {
        assertNull(ButBranch.newBranchNameError("feature-a"))
        assertNull(ButBranch.newBranchNameError("feature/ena-5068/auth_debug"))
        assertNull(ButBranch.newBranchNameError("v1.2.3"))
        assertNull(ButBranch.newBranchNameError("fix.lockfile"))
    }

    @Test
    fun emptyName_isRejected() {
        assertEquals("Branch name must not be empty", ButBranch.newBranchNameError(""))
    }

    @Test
    fun whitespace_isRejected() {
        assertEquals("Branch name must not contain whitespace", ButBranch.newBranchNameError("bad name"))
        assertEquals("Branch name must not contain whitespace", ButBranch.newBranchNameError("tab\there"))
    }

    @Test
    fun controlCharacters_areRejected() {
        assertEquals(
            "Branch name must not contain control characters",
            ButBranch.newBranchNameError("bad\u0001name"),
        )
    }

    @Test
    fun gitIllegalCharacters_areRejected() {
        listOf("a~b", "a^b", "a:b", "a?b", "a*b", "a[b", "a\\b").forEach { name ->
            assertNotNull(ButBranch.newBranchNameError(name), "expected '$name' to be rejected")
        }
    }

    @Test
    fun doubleDotAndReflogSyntax_areRejected() {
        assertEquals("Branch name must not contain '..'", ButBranch.newBranchNameError("a..b"))
        assertEquals("Branch name must not contain '@{'", ButBranch.newBranchNameError("a@{1}"))
        assertEquals("'@' is not a valid branch name", ButBranch.newBranchNameError("@"))
    }

    @Test
    fun leadingDash_isRejected() {
        assertEquals("Branch name must not start with '-'", ButBranch.newBranchNameError("-feature"))
    }

    @Test
    fun slashEdges_areRejected() {
        val message = "Branch name must not start or end with '/' or contain '//'"
        assertEquals(message, ButBranch.newBranchNameError("/feature"))
        assertEquals(message, ButBranch.newBranchNameError("feature/"))
        assertEquals(message, ButBranch.newBranchNameError("feature//a"))
    }

    @Test
    fun trailingDotAndDotComponents_areRejected() {
        assertEquals("Branch name must not end with '.'", ButBranch.newBranchNameError("feature."))
        assertEquals("No part of a branch name may start with '.'", ButBranch.newBranchNameError(".hidden"))
        assertEquals("No part of a branch name may start with '.'", ButBranch.newBranchNameError("feature/.hidden"))
        assertEquals(
            "No part of a branch name may end with '.lock'",
            ButBranch.newBranchNameError("feature/a.lock"),
        )
    }

    @Test
    fun workspaceBranch_isRejected() {
        assertEquals(
            "'gitbutler/workspace' is GitButler's own workspace branch",
            ButBranch.newBranchNameError(ButClient.WORKSPACE_BRANCH),
        )
    }

    @Test
    fun existingLookingName_isNotRejectedHere() {
        // Uniqueness is the CLI's call: `but commit -b` legitimately targets an existing branch.
        assertNull(ButBranch.newBranchNameError("main"))
    }
}
