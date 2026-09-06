/*
 * Copyright (c) Mikolaj Olszewski 2026-9-6.
 */

package eu.mikus.edziennik.ui.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ContrastTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) {
        assertTrue(abs(expected - actual) <= tolerance, "expected ~$expected but was $actual")
    }

    @Test
    fun `black on white is the maximum ratio`() {
        assertClose(21f, Color.Black.contrastRatio(Color.White))
    }

    @Test
    fun `a colour against itself is the minimum ratio`() {
        assertClose(1f, Color(0xFF842EA8).contrastRatio(Color(0xFF842EA8)))
    }

    @Test
    fun `the ratio is symmetric`() {
        assertClose(Color(0xFFE0E2EA).contrastRatio(Color(0xFFFF5E5E)),
                    Color(0xFFFF5E5E).contrastRatio(Color(0xFFE0E2EA)))
    }

    @Test
    fun `the Red theme's shipped ink is the measured 1_76 to 1`() {
        // BlueDarkColors.onSurfaceVariant over colorSurfaceRed - the defect this phase fixes.
        assertClose(1.76f, Color(0xFFC0C7D3).contrastRatio(Color(0xFFFF5E5E)))
    }

    @Test
    fun `blendTowards interpolates per channel in sRGB, not Oklab`() {
        // sRGB midpoint of black and white is 0.5 per channel. Oklab's is about 0.39.
        val mid = Color.Black.blendTowards(Color.White, 0.5f)
        assertClose(0.5f, mid.red, tolerance = 0.01f)
        assertClose(0.5f, mid.green, tolerance = 0.01f)
    }

    @Test
    fun `blendTowards at the endpoints returns the endpoints`() {
        val a = Color(0xFF181C21)
        val b = Color(0xFFFFFFFF)
        assertClose(a.luminance(), a.blendTowards(b, 0f).luminance(), 0.001f)
        assertClose(b.luminance(), a.blendTowards(b, 1f).luminance(), 0.001f)
    }

    @Test
    fun `solveForLuminance hits the requested fraction of the span`() {
        val surface = Color(0xFF101419)
        val ink = Color(0xFFE0E2EA)
        val t = 0.0375f
        val got = solveForLuminance(surface, ink, t)
        val want = surface.luminance() + t * (ink.luminance() - surface.luminance())
        assertClose(want, got.luminance(), 0.002f)
    }

    @Test
    fun `solveForLuminance with a negative fraction moves away from the ink`() {
        val surface = Color(0xFF101419)
        val ink = Color(0xFFE0E2EA)
        val got = solveForLuminance(surface, ink, -0.0033f)
        assertTrue(got.luminance() < surface.luminance(), "expected darker than surface, got $got")
    }

    @Test
    fun `withMinContrast leaves an already-compliant ink untouched`() {
        val ink = Color(0xFF181C21)
        assertClose(ink.luminance(), ink.withMinContrast(listOf(Color.White)).luminance(), 0.0001f)
    }

    @Test
    fun `withMinContrast satisfies the worst ground, not merely the first`() {
        // #C0C7D3 scores 10.87 on the first ground and 4.21 on the second, so an implementation that
        // stops at the first ground returns it unchanged and fails here.
        val ink = Color(0xFFC0C7D3)
        val grounds = listOf(Color(0xFF101419), Color(0xFF842EA8))
        val fixed = ink.withMinContrast(grounds)
        assertTrue(fixed.luminance() != ink.luminance(), "the ink should have moved")
        grounds.forEach {
            assertTrue(fixed.contrastRatio(it) >= 4.5f, "ground $it was ${fixed.contrastRatio(it)}")
        }
    }

    @Test
    fun `withMinContrast clears the ratio on the Purple theme's real grounds`() {
        // Compose stores sRGB at 8 bits per channel, and blendTowards constructs a Color per
        // iteration, so the search already tests candidates at the precision they are stored. This
        // guards the outcome on the tightest real input, not that property: quantising only after
        // the search would land at 4.4875 here, which is why the search must not be restructured
        // to do that.
        val ink = Color(0xFFC0C7D3)
        val grounds = listOf(Color(0xFF842EA8), Color(0xFF8C3FAE), Color(0xFF934BB3))
        val fixed = ink.withMinContrast(grounds)
        grounds.forEach {
            assertTrue(fixed.contrastRatio(it) >= 4.5f, "ground $it was ${fixed.contrastRatio(it)}")
        }
    }
}
