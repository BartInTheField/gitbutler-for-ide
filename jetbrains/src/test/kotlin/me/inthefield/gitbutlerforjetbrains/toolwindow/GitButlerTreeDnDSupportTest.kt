package me.inthefield.gitbutlerforjetbrains.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the pure selection/target rules [GitButlerTreeDnD] enforces before touching Swing or
 * the IDE platform: only all-[DnDRow.Change] selections drag, only [DnDRow.Branch]/
 * [DnDRow.Commit] rows accept drops.
 */
class GitButlerTreeDnDSupportTest {

    @Test
    fun draggableChangePaths_allChanges_returnsPaths() {
        val payloads = listOf(DnDRow.Change("a.txt"), DnDRow.Change("dir/b.txt"))
        assertEquals(listOf("a.txt", "dir/b.txt"), GitButlerTreeDnD.draggableChangePaths(payloads))
    }

    @Test
    fun draggableChangePaths_mixedSelection_returnsNull() {
        val payloads = listOf(DnDRow.Change("a.txt"), DnDRow.Branch("main"))
        assertNull(GitButlerTreeDnD.draggableChangePaths(payloads))
    }

    @Test
    fun draggableChangePaths_emptySelection_returnsNull() {
        assertNull(GitButlerTreeDnD.draggableChangePaths(emptyList()))
    }

    @Test
    fun draggableChangePaths_unresolvedRow_returnsNull() {
        val payloads = listOf(DnDRow.Change("a.txt"), null)
        assertNull(GitButlerTreeDnD.draggableChangePaths(payloads))
    }

    @Test
    fun dropTarget_branch_isAccepted() {
        val branch = DnDRow.Branch("main")
        assertEquals(branch, GitButlerTreeDnD.dropTarget(branch))
    }

    @Test
    fun dropTarget_commit_isAccepted() {
        val commit = DnDRow.Commit("cli-1", "message")
        assertEquals(commit, GitButlerTreeDnD.dropTarget(commit))
    }

    @Test
    fun dropTarget_commitWithBlankCliId_isRejected() {
        assertNull(GitButlerTreeDnD.dropTarget(DnDRow.Commit("", "message")))
    }

    @Test
    fun dropTarget_change_isRejected() {
        assertNull(GitButlerTreeDnD.dropTarget(DnDRow.Change("a.txt")))
    }

    @Test
    fun dropTarget_null_isRejected() {
        assertNull(GitButlerTreeDnD.dropTarget(null))
    }
}
