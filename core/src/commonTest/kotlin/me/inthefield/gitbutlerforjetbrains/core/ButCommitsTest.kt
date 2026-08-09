package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ButCommitsTest {

    @Test
    fun effectiveId_prefersCliId() {
        assertEquals("cli-1", ButCommits.effectiveId("cli-1", "abcdef0"))
    }

    @Test
    fun effectiveId_fallsBackToShaWhenCliIdBlank() {
        assertEquals("abcdef0", ButCommits.effectiveId("", "abcdef0"))
        assertEquals("abcdef0", ButCommits.effectiveId("   ", "abcdef0"))
    }

    @Test
    fun shortId_takesFirstSevenChars() {
        assertEquals("abcdef0", ButCommits.shortId("abcdef0123456789"))
        assertEquals("abc", ButCommits.shortId("abc"))
    }

    @Test
    fun summary_returnsFirstLine() {
        assertEquals("add hello", ButCommits.summary("add hello\n\nbody paragraph"))
    }

    @Test
    fun summary_emptyMessageYieldsEmpty() {
        assertEquals("", ButCommits.summary(""))
    }
}
