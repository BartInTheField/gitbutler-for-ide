package me.inthefield.gitbutlerforjetbrains.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Pins the selection rules the status panel's toolbar/context-menu actions are gated on:
 * a branch-scoped action finds the branch of any row *under* a branch, and a change-scoped
 * action only accepts a selection made entirely of change rows.
 *
 * Stand-in payloads keep the test independent of the panel's private node classes, which is
 * exactly what [GitButlerTreeSelection]'s `pick` lambda exists for.
 */
class GitButlerTreeSelectionTest {

    private data class Branch(val name: String)
    private data class Change(val path: String)
    private object Group

    private fun pickBranch(payload: Any?) = payload as? Branch
    private fun pickChangePath(payload: Any?) = (payload as? Change)?.path

    /** root → branch("feature-a") → commit group → change("a.txt"). */
    private fun nestedTree(): Map<String, DefaultMutableTreeNode> {
        val root = DefaultMutableTreeNode()
        val branch = DefaultMutableTreeNode(Branch("feature-a"))
        val group = DefaultMutableTreeNode(Group)
        val change = DefaultMutableTreeNode(Change("a.txt"))
        root.add(branch)
        branch.add(group)
        group.add(change)
        return mapOf("root" to root, "branch" to branch, "group" to group, "change" to change)
    }

    @Test
    fun ancestorPayload_onTheNodeItself_resolves() {
        val nodes = nestedTree()
        assertEquals(Branch("feature-a"), GitButlerTreeSelection.ancestorPayload(nodes["branch"], ::pickBranch))
    }

    @Test
    fun ancestorPayload_fromNestedRow_findsEnclosingBranch() {
        val nodes = nestedTree()
        assertEquals(Branch("feature-a"), GitButlerTreeSelection.ancestorPayload(nodes["group"], ::pickBranch))
        assertEquals(Branch("feature-a"), GitButlerTreeSelection.ancestorPayload(nodes["change"], ::pickBranch))
    }

    @Test
    fun ancestorPayload_outsideEveryBranch_isNull() {
        // A top-level row — the "Unassigned changes" lane — has no branch above it.
        val root = DefaultMutableTreeNode()
        val unassigned = DefaultMutableTreeNode(Change("loose.txt"))
        root.add(unassigned)
        assertNull(GitButlerTreeSelection.ancestorPayload(unassigned, ::pickBranch))
    }

    @Test
    fun ancestorPayload_nullNode_isNull() {
        assertNull(GitButlerTreeSelection.ancestorPayload(null, ::pickBranch))
    }

    @Test
    fun allOrNull_homogeneousSelection_mapsEveryRow() {
        val payloads = listOf<Any?>(Change("a.txt"), Change("dir/b.txt"))
        assertEquals(listOf("a.txt", "dir/b.txt"), GitButlerTreeSelection.allOrNull(payloads, ::pickChangePath))
    }

    @Test
    fun allOrNull_mixedSelection_isNull() {
        val payloads = listOf<Any?>(Change("a.txt"), Branch("feature-a"))
        assertNull(GitButlerTreeSelection.allOrNull(payloads, ::pickChangePath))
    }

    @Test
    fun allOrNull_emptyOrUnresolvedSelection_isNull() {
        assertNull(GitButlerTreeSelection.allOrNull(emptyList(), ::pickChangePath))
        assertNull(GitButlerTreeSelection.allOrNull(listOf<Any?>(Change("a.txt"), null), ::pickChangePath))
    }
}
