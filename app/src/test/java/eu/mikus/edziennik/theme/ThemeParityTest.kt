/*
 * Copyright (c) Mikolaj Olszewski 2026-9-6.
 */

package eu.mikus.edziennik.theme

import android.app.Application
import android.util.TypedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.theme.BlueLightColors
import eu.mikus.edziennik.ui.compose.theme.appColorScheme
import eu.mikus.edziennik.ui.compose.theme.contrastRatio
import eu.mikus.edziennik.ui.compose.theme.schemeFor
import eu.mikus.edziennik.utils.Themes
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The gate for Phase 34 (N5c): the Compose scheme must agree with the XML theme the user picked.
 *
 * Deliberately does NOT reuse `ThemeAttrProbe`'s resolver. That one does
 * `if (!theme.resolveAttribute(id, tv, true)) continue`, silently dropping an attribute it cannot
 * resolve - so a missing attribute would read here as a pass. `NavlibCompat.kt:27-28` has the mirror
 * hazard, returning `TypedValue().data` (transparent) rather than reporting failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35], qualifiers = "en-rUS-notnight-xhdpi")
class ThemeParityTest {

    private var savedTheme = Themes.theme

    @Before fun saveTheme() { savedTheme = Themes.theme }
    @After fun restoreTheme() { Themes.theme = savedTheme }

    private fun resolve(styleRes: Int, attr: Int): Int {
        val theme = RuntimeEnvironment.getApplication().resources.newTheme()
        theme.applyStyle(styleRes, true)
        val tv = TypedValue()
        assertTrue(theme.resolveAttribute(attr, tv, true),
            "attribute 0x${attr.toString(16)} did not resolve against style $styleRes")
        return tv.data
    }

    @Test
    fun `every theme's background and surface match its XML style`() {
        // Anti-vacuity floor: a loop over an empty list passes. ThemeAttrProbe carries the same guard.
        assertEquals(18, Themes.themeList.size, "the picker no longer has eighteen entries")

        val mismatches = mutableListOf<String>()
        for (entry in Themes.themeList) {
            val scheme = schemeFor(entry.id)
            val xmlBackground = resolve(entry.style, android.R.attr.colorBackground)
            val xmlSurface = resolve(entry.style, R.attr.colorSurface)
            if (scheme.background.toArgb() != xmlBackground) {
                mismatches += "id=${entry.id} background compose=${hex(scheme.background)} xml=${hex(xmlBackground)}"
            }
            if (scheme.surface.toArgb() != xmlSurface) {
                mismatches += "id=${entry.id} surface compose=${hex(scheme.surface)} xml=${hex(xmlSurface)}"
            }
        }
        // Collect then assert once: bare assertEquals throws on the first row and reports one failure
        // instead of the full list.
        assertTrue(mismatches.isEmpty(), "${mismatches.size} parity mismatches:\n" + mismatches.joinToString("\n"))
    }

    /**
     * The adapter rows. These are what land red before the switch task, and what would catch a
     * delegation that passes the wrong id or drops [forceLight].
     */
    @Test
    fun `appColorScheme returns the selected theme's scheme`() {
        assertEquals(18, Themes.themeList.size)
        val app = RuntimeEnvironment.getApplication()
        val mismatches = mutableListOf<String>()
        for (entry in Themes.themeList) {
            Themes.theme = entry
            val actual = appColorScheme(app)
            val expected = schemeFor(entry.id)
            // ColorScheme has no equals(), so compare the fields this phase sets.
            if (actual.background != expected.background) {
                mismatches += "id=${entry.id} background ${hex(actual.background)} != ${hex(expected.background)}"
            }
            if (actual.surface != expected.surface) {
                mismatches += "id=${entry.id} surface ${hex(actual.surface)} != ${hex(expected.surface)}"
            }
            if (actual.onSurfaceVariant != expected.onSurfaceVariant) {
                mismatches += "id=${entry.id} onSurfaceVariant ${hex(actual.onSurfaceVariant)} != ${hex(expected.onSurfaceVariant)}"
            }
        }
        assertTrue(mismatches.isEmpty(), "${mismatches.size} adapter mismatches:\n" + mismatches.joinToString("\n"))
    }

    @Test
    fun `appColorScheme honours forceLight for every theme`() {
        val app = RuntimeEnvironment.getApplication()
        for (entry in Themes.themeList) {
            Themes.theme = entry
            assertEquals(BlueLightColors, appColorScheme(app, forceLight = true), "id=${entry.id}")
        }
    }

    @Test
    fun `every theme's ink clears WCAG AA on every ground text lands on`() {
        assertEquals(18, Themes.themeList.size)
        val failures = mutableListOf<String>()
        for (entry in Themes.themeList) {
            val s = schemeFor(entry.id)
            val grounds = listOf(
                s.background, s.surface, s.surfaceContainerLowest, s.surfaceContainerLow,
                s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceVariant,
            )
            for (ground in grounds) {
                val a = s.onSurface.contrastRatio(ground)
                val b = s.onSurfaceVariant.contrastRatio(ground)
                if (a < 4.5f) failures += "id=${entry.id} onSurface on ${hex(ground)} = $a"
                if (b < 4.5f) failures += "id=${entry.id} onSurfaceVariant on ${hex(ground)} = $b"
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} contrast failures:\n" + failures.joinToString("\n"))
    }

    private fun hex(c: Color) = "#%08x".format(c.toArgb())
    private fun hex(argb: Int) = "#%08x".format(argb)
}
