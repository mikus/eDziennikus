/*
 * Copyright (c) Mikolaj Olszewski 2026-9-14.
 */

package eu.mikus.edziennik.ui.shell

import eu.mikus.edziennik.ui.compose.theme.schemeFor
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * The phase's one real gate (§6.1 of the Phase 38 design).
 *
 * It deliberately does NOT assert that the two roles contrast: `ColorSchemeDerivationTest` already
 * walks all 18 themes asserting `onSurface` against a `textGrounds` list that includes
 * `surfaceContainer`, so that would be a tautology over an already-green assertion. What is unproven
 * is that the light arm *returns those two roles* - a property of this phase's code, not of the
 * scheme.
 *
 * The wiring - that `BottomAppBar` actually receives this - is NOT covered here and cannot be (§6.2);
 * mutations D, E and F in the plan are exactly that gap, and the emulator capture is what covers it.
 */
class AppBottomBarColorsTest {

    /** `Themes.kt`: the 7 entries with `isDark = false`. */
    private val lightThemeIds = listOf(0, 5, 8, 11, 14, 16, 17)

    @Test
    fun `the light bar takes its container and ink from the scheme`() {
        for (id in lightThemeIds) {
            val scheme = schemeFor(id)
            val colors = lightBarColors(scheme)
            assertEquals(scheme.surfaceContainer, colors.container, "theme $id container")
            assertEquals(scheme.onSurface, colors.content, "theme $id ink")
        }
    }
}
