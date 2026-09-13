/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.ui.base.enums

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the predicate only. The route that consumes it lives in `MainActivity.handleIntent`, which
 * is unreachable from the JVM suite - deleting the call there ships the defect with every test still
 * green. The wiring is covered by the `am start` check in this task's smoke step, not here.
 */
class NavTargetTest {

    @Test
    fun `a devModeOnly target is unavailable outside dev mode`() {
        assertFalse(NavTarget.LAB.isAvailable(devMode = false), "LAB exposes stored credentials")
        assertFalse(NavTarget.DEBUG.isAvailable(devMode = false))
    }

    @Test
    fun `dev mode restores a devModeOnly target`() {
        assertTrue(NavTarget.LAB.isAvailable(devMode = true))
        assertTrue(NavTarget.DEBUG.isAvailable(devMode = true))
    }

    @Test
    fun `an ordinary target is available either way`() {
        // Anti-vacuity: a predicate that refused everything would satisfy test 1 on its own.
        assertTrue(NavTarget.HOME.isAvailable(devMode = false))
        assertTrue(NavTarget.HOME.isAvailable(devMode = true))
    }
}
