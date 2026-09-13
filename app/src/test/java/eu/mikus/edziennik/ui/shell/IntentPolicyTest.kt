/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.ui.shell

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the predicate only. The `trustedOrigin` wiring lives in `MainActivity.handleIntent`, which
 * is unreachable from the JVM suite - dropping the guard there ships the defect with every test
 * still green, so it is covered by this task's `am start` / `am broadcast` pair.
 *
 * The "serverMessage" case below is documentation of a removed action, NOT coverage for its removal:
 * this function never inspects `MainActivity`, so the assertion passes identically whether or not
 * that branch exists. The deletion's only protection is its own absence-grep, run once, plus the
 * smoke. It is named here so a future reader learns the action was deliberately dropped.
 */
class IntentPolicyTest {

    @Test
    fun `a broadcast-only action is refused from a launch intent`() {
        assertFalse(actionAllowedFromLaunch("createManualEvent"))
    }

    @Test
    fun `the two payload-free actions are allowed from a launch intent`() {
        // Anti-vacuity: a predicate refusing everything would pass the other three tests.
        assertTrue(actionAllowedFromLaunch("updateRequest"))
        assertTrue(actionAllowedFromLaunch("userActionRequired"))
    }

    @Test
    fun `an unknown action is refused`() {
        assertFalse(actionAllowedFromLaunch("serverMessage"))
        assertFalse(actionAllowedFromLaunch("anything"))
    }

    @Test
    fun `a null action is refused`() {
        assertFalse(actionAllowedFromLaunch(null))
    }
}
