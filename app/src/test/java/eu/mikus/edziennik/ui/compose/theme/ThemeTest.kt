/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ThemeTest {

    // The light/dark cases that used to live here are subsumed by ThemeParityTest's rows 0 and 1,
    // which assert against the XML rather than against a hard-coded scheme.

    @Test
    fun `black theme keeps a pure-black background and AMOLED containers`() {
        val scheme = schemeFor(themeId = 2)
        assertEquals(Color(0xFF000000), scheme.background)
        // The XML's own AppTheme.Black surface, which Compose previously contradicted with #000000.
        assertEquals(Color(0xFF121212), scheme.surface)
        // Lock the full override (M3 cards tint from surfaceContainer*), not just background/surface.
        assertEquals(Color(0xFF000000), scheme.surfaceContainer)
    }
}
