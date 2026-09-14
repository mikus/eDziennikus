/*
 * Copyright (c) Mikolaj Olszewski 2026-9-14.
 */

package eu.mikus.edziennik.ui.shell

import eu.mikus.edziennik.ui.compose.theme.contrastRatio
import eu.mikus.edziennik.ui.compose.theme.schemeFor
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    @Test
    fun `every theme takes the bar's container and ink from the scheme`() {
        // All 18, not just the 7 light ones. The dark arm used to keep navlib's
        // blend(?colorSurface, colorSurface_4dp), which rendered #454545 against a #454646 Home card
        // - 1.0125:1, so the card's bottom edge was invisible and the bar looked like it was
        // covering content. Reported from the field.
        for (id in 0..17) {
            val scheme = schemeFor(id)
            val colors = barColorsFor(scheme)
            assertEquals(scheme.surfaceContainer, colors.container, "theme $id container")
            assertEquals(scheme.onSurface, colors.content, "theme $id ink")
        }
    }

    @Test
    fun `the bar is always distinguishable from the card it sits under`() {
        // Home cards render on surfaceContainerHighest; the bar on surfaceContainer. One ramp step
        // apart is the whole point - a bar the user cannot find the edge of reads as a bar that is
        // covering things. The old dark blend measured 1.0125:1 against that card.
        for (id in 0..17) {
            val s = schemeFor(id)
            val ratio = barColorsFor(s).container.contrastRatio(s.surfaceContainerHighest)
            assertTrue(ratio >= 1.09f, "theme $id bar/card separation was $ratio")
        }
    }
}
