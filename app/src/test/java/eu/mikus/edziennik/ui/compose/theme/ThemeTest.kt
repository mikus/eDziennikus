/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ThemeTest {

    @Test
    fun `light theme resolves to the light Blue scheme`() {
        assertEquals(BlueLightColors, resolveColorScheme(isDark = false, isBlack = false))
    }

    @Test
    fun `dark theme resolves to the dark Blue scheme`() {
        assertEquals(BlueDarkColors, resolveColorScheme(isDark = true, isBlack = false))
    }

    @Test
    fun `black theme forces a pure-black background`() {
        val scheme = resolveColorScheme(isDark = true, isBlack = true)
        assertEquals(Color(0xFF000000), scheme.background)
        assertEquals(Color(0xFF000000), scheme.surface)
        // Lock the full override (M3 cards tint from surfaceContainer*), not just background/surface.
        assertEquals(Color(0xFF000000), scheme.surfaceContainer)
    }
}
