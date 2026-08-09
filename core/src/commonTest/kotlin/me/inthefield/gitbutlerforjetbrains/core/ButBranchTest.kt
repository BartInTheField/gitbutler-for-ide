package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
