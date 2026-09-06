/*
 * Copyright (c) Mikolaj Olszewski 2026-9-6.
 */

package eu.mikus.edziennik.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import eu.mikus.edziennik.utils.Themes
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ColorSchemeDerivationTest {

    private val blackThemeId = 2

    /** Every ground a text role is drawn on. surfaceDim/surfaceBright are decorative. */
    private fun textGrounds(s: ColorScheme) = listOf(
        s.background, s.surface, s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
        s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceVariant,
    )

    private fun ramp(s: ColorScheme) = listOf(
        s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
        s.surfaceContainerHigh, s.surfaceContainerHighest,
    ).map { it.luminance() }

    @Test
    fun `the table covers exactly the picker's entries`() {
        assertEquals(Themes.themeList.map { it.id }.toSet(), ThemeSurfaces.keys)
        assertEquals(18, ThemeSurfaces.size)
    }

    @Test
    fun `an unmapped theme fails loudly rather than falling back to Blue`() {
        assertTrue(runCatching { schemeFor(18) }.exceptionOrNull() != null,
            "schemeFor(18) must throw, not return the Blue scheme")
    }

    @Test
    fun `background and surface come from the table`() {
        assertEquals(Color(0xFFFFD54F), schemeFor(16).background)   // Amber
        assertEquals(Color(0xFFFFD75B), schemeFor(16).surface)
        assertEquals(Color(0xFF000000), schemeFor(2).background)    // Black
        assertEquals(Color(0xFF121212), schemeFor(2).surface)
        assertEquals(Color(0xFFFFFFFF), schemeFor(0).surface)       // Light, from styles.xml
    }

    /**
     * THE FALSIFIER for the ramp. Rebuilding a Blue scheme from its own surface and its own offsets
     * must reproduce it. An earlier draft applied the offsets as sRGB blend fractions instead of
     * luminance fractions and returned #181c21 here against the real #31353b - and every other
     * assertion still passed, because monotonicity survives any consistent scalar.
     */
    @Test
    fun `rebuilding the Blue schemes from their own offsets reproduces them`() {
        val light = BlueLightColors.withSurfaces(BlueLightColors.background, BlueLightColors.surface)
        val dark = BlueDarkColors.withSurfaces(BlueDarkColors.background, BlueDarkColors.surface)
        fun assertNear(expected: Color, actual: Color, what: String) {
            // 0.006, not 0.004: near L=0.95 a single 8-bit channel step is worth ~0.0057 of
            // luminance, so the solver's grid is coarser than a tighter bound on light surfaces.
            // The unit error this test exists to catch is worth ~0.03, so the margin is ample.
            assertTrue(abs(expected.luminance() - actual.luminance()) < 0.006f,
                "$what: expected ~$expected but was $actual")
        }
        assertNear(BlueLightColors.surfaceContainerHighest, light.surfaceContainerHighest, "light Highest")
        assertNear(BlueLightColors.surfaceContainer, light.surfaceContainer, "light Container")
        assertNear(BlueDarkColors.surfaceContainerHighest, dark.surfaceContainerHighest, "dark Highest")
        assertNear(BlueDarkColors.surfaceContainer, dark.surfaceContainer, "dark Container")
        assertNear(BlueDarkColors.surfaceVariant, dark.surfaceVariant, "dark Variant")
    }

    @Test
    fun `every theme clears WCAG AA on every ground text lands on`() {
        for (id in 0..17) {
            val s = schemeFor(id)
            for (ground in textGrounds(s)) {
                assertTrue(s.onSurface.contrastRatio(ground) >= 4.5f,
                    "theme $id onSurface on $ground was ${s.onSurface.contrastRatio(ground)}")
                assertTrue(s.onSurfaceVariant.contrastRatio(ground) >= 4.5f,
                    "theme $id onSurfaceVariant on $ground was ${s.onSurfaceVariant.contrastRatio(ground)}")
            }
        }
    }

    @Test
    fun `only the measured themes have their ink moved, and only the measured role`() {
        val lightInk = Color(0xFF181C21)
        val darkInk = Color(0xFFE0E2EA)
        val lightVariant = Color(0xFF404752)
        val darkVariant = Color(0xFFC0C7D3)
        val onSurfaceMoved = (0..17).filter {
            schemeFor(it).onSurface !in listOf(lightInk, darkInk)
        }
        val onSurfaceVariantMoved = (0..17).filter {
            schemeFor(it).onSurfaceVariant !in listOf(lightVariant, darkVariant)
        }
        assertEquals(listOf(7, 10), onSurfaceMoved, "onSurface")
        // DarkBlue (6) is in this list and easy to miss by hand: its base onSurfaceVariant scores
        // 4.4576 against its own surfaceVariant, so it is nudged one 8-bit step to #C1C8D3.
        assertEquals(listOf(3, 4, 6, 7, 10, 13, 15), onSurfaceVariantMoved, "onSurfaceVariant")
    }

    @Test
    fun `the container ramp is monotonic, and Black is the one documented exception`() {
        for (id in 0..17) {
            val steps = ramp(schemeFor(id))
            if (id == blackThemeId) {
                // The AMOLED clamp pins Lowest/Low/Container to #000000, so the ramp is flat at the
                // bottom by design. Non-strict here, strict everywhere else.
                assertTrue(steps.zipWithNext().all { (a, b) -> a <= b },
                    "Black's ramp must still be non-decreasing: $steps")
            } else {
                val up = steps.zipWithNext().all { (a, b) -> a < b }
                val down = steps.zipWithNext().all { (a, b) -> a > b }
                assertTrue(up || down, "theme $id ramp was not monotonic: $steps")
            }
        }
    }

    /**
     * The clause that catches a mis-scaled ramp. Under the earlier unit error the Dark theme's
     * adjacent steps were 0.0012-0.0038 - strictly monotonic and therefore green. Measured minimum
     * across the 17 non-Black themes is 0.00209 (Purple), so 0.001 is a floor with headroom.
     */
    @Test
    fun `adjacent ramp roles differ by a visible step`() {
        for (id in 0..17) {
            if (id == blackThemeId) continue
            val steps = ramp(schemeFor(id)).zipWithNext().map { (a, b) -> abs(b - a) }
            assertTrue(steps.all { it >= 0.001f }, "theme $id has a near-invisible ramp step: $steps")
        }
    }

    @Test
    fun `surfaceDim and surfaceBright bracket surface`() {
        for (id in 0..17) {
            val s = schemeFor(id)
            val lo = minOf(s.surfaceDim.luminance(), s.surfaceBright.luminance())
            val hi = maxOf(s.surfaceDim.luminance(), s.surfaceBright.luminance())
            assertTrue(lo <= s.surface.luminance() + 1e-6f && s.surface.luminance() <= hi + 1e-6f,
                "theme $id: surface not bracketed by dim/bright")
        }
    }

    @Test
    fun `the Black theme keeps its AMOLED clamp`() {
        val s = schemeFor(2)
        assertEquals(Color(0xFF000000), s.surfaceContainerLowest)
        assertEquals(Color(0xFF000000), s.surfaceContainerLow)
        assertEquals(Color(0xFF000000), s.surfaceContainer)
    }

    @Test
    fun `forceLight is the Blue light scheme for every theme`() {
        for (id in 0..17) {
            assertEquals(BlueLightColors, schemeFor(id, forceLight = true), "theme $id")
        }
    }

    @Test
    fun `the base scheme is chosen by isDark, so primary never moves`() {
        for (entry in Themes.themeList) {
            val expected = if (entry.isDark) BlueDarkColors.primary else BlueLightColors.primary
            assertEquals(expected, schemeFor(entry.id).primary, "theme ${entry.id}")
        }
        // Red is the one theme whose surface luminance disagrees with its isDark flag.
        assertEquals(BlueDarkColors.primary, schemeFor(13).primary)
    }
}
