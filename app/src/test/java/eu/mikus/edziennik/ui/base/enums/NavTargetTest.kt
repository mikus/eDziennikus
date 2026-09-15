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
    }

    @Test
    fun `dev mode restores a devModeOnly target`() {
        assertTrue(NavTarget.LAB.isAvailable(devMode = true))
    }

    @Test
    fun `an ordinary target is available either way`() {
        // Anti-vacuity: a predicate that refused everything would satisfy test 1 on its own.
        assertTrue(NavTarget.HOME.isAvailable(devMode = false))
        assertTrue(NavTarget.HOME.isAvailable(devMode = true))
    }

    @Test
    fun `no target claims the bottom sheet, which four KDoc blocks now assert`() {
        // Phase 40 deleted DEBUG, BOTTOM_SHEET's last member. NavTargetLocation.BOTTOM_SHEET,
        // MainActivity.sheetBaseRows and AppSheet/AppScaffold's baseRows parameter were all kept as
        // the seam a future sheet-only target plugs into - and their KDoc says the list is empty.
        // Adding such a target is allowed; letting those four blocks go stale silently is not.
        assertTrue(NavTarget.values().none { it.location == NavTargetLocation.BOTTOM_SHEET })
    }
}
