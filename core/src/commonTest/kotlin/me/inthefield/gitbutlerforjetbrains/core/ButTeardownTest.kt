package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButTeardownTest {

    @Test
    fun checkoutCandidates_appliedFirst_thenOtherLocals_skipsWorkspaceAndBlanks() {
        assertEquals(
            listOf("feature", "main"),
            ButTeardown.checkoutCandidates(
                appliedNames = listOf("feature", "gitbutler/workspace", " "),
                localNames = listOf("main", "feature", "gitbutler/workspace", ""),
            ),
        )
    }

    @Test
    fun hasCheckoutDefault_requiresARealAppliedBranch() {
        assertFalse(ButTeardown.hasCheckoutDefault(emptyList()))
        assertFalse(ButTeardown.hasCheckoutDefault(listOf("  ", "gitbutler/workspace")))
        assertTrue(ButTeardown.hasCheckoutDefault(listOf("gitbutler/workspace", "feature")))
    }
}
