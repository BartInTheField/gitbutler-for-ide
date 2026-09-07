package me.inthefield.gitbutlerforjetbrains.toolwindow

import javax.swing.tree.DefaultMutableTreeNode

/**
 * The pure tree-walk rules behind the status panel's selection-driven actions, kept out of
 * [GitButlerStatusPanel] so they can be unit-tested without a Swing or IDE fixture — the same
 * split [GitButlerTreeDnD] uses for its drag/drop rules. Both take a `pick` lambda so the
 * panel's own node payload types stay private to it.
 */
internal object GitButlerTreeSelection {

    /**
     * The first payload that [pick] resolves, searching [node] itself and then its ancestors.
     * Null when no row on the path to the root carries one. Lets an action target the branch a
     * nested row (a commit, a file in a commit, an assigned change) belongs to.
     */
    fun <T : Any> ancestorPayload(node: DefaultMutableTreeNode?, pick: (Any?) -> T?): T? {
        var current = node
        while (current != null) {
            pick(current.userObject)?.let { return it }
            current = current.parent as? DefaultMutableTreeNode
        }
        return null
    }

    /**
     * [payloads] mapped through [pick] if *every* one resolves, else null — so an action that
     * needs a homogeneous selection (e.g. "these change rows") refuses a mixed or empty one.
     */
    fun <T : Any> allOrNull(payloads: List<Any?>, pick: (Any?) -> T?): List<T>? {
        if (payloads.isEmpty()) return null
        return payloads.map { pick(it) ?: return null }
    }
}
