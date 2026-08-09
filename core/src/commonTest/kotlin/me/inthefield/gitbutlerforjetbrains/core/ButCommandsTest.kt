package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ButCommandsTest {

    @Test
    fun status_buildsJsonArgs() {
        assertEquals(listOf("status", "-f", "--json"), ButCommands.status())
    }

    @Test
    fun push_buildsBranchArgs() {
        assertEquals(listOf("push", "feature-a", "--json"), ButCommands.push("feature-a"))
    }

    @Test
    fun pull_buildsJsonArgs() {
        assertEquals(listOf("pull", "--json"), ButCommands.pull())
    }

    @Test
    fun apply_buildsBranchArgs() {
        assertEquals(listOf("apply", "feature-a", "--json"), ButCommands.apply("feature-a"))
    }

    @Test
    fun unapply_buildsBranchArgs() {
        assertEquals(listOf("unapply", "feature-a", "--json"), ButCommands.unapply("feature-a"))
    }

    @Test
    fun commit_passesCliIdsPositionally() {
        assertEquals(
            listOf("commit", "-b", "feature-a", "-m", "add hello", "--json", "aa", "bb"),
            ButCommands.commit("feature-a", "add hello", listOf("aa", "bb")),
        )
    }

    @Test
    fun commit_singleCliId() {
        assertEquals(
            listOf("commit", "-b", "feature-a", "-m", "msg", "--json", "aa"),
            ButCommands.commit("feature-a", "msg", listOf("aa")),
        )
    }

    @Test
    fun amend_passesCliIdsPositionally() {
        assertEquals(
            listOf("amend", "-t", "commit-1", "--json", "aa", "bb"),
            ButCommands.amend("commit-1", listOf("aa", "bb")),
        )
    }

    @Test
    fun amend_singleCliId() {
        assertEquals(
            listOf("amend", "-t", "commit-1", "--json", "aa"),
            ButCommands.amend("commit-1", listOf("aa")),
        )
    }

    @Test
    fun uncommit_buildsCommitIdArgs() {
        assertEquals(listOf("uncommit", "commit-1", "--json"), ButCommands.uncommit("commit-1"))
    }

    @Test
    fun reword_buildsMessageArgs() {
        assertEquals(
            listOf("reword", "commit-1", "-m", "new message", "--json"),
            ButCommands.reword("commit-1", "new message"),
        )
    }
}
