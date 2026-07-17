/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.mikus.edziennik.utils.Themes

/**
 * Maps the app's [Themes] selection to a static Compose [ColorScheme].
 * Pure (no Context, no dynamic color) so it is unit-testable. Phase 0 collapses
 * the 15 colored Themes (ids 3-17) to the Blue fallback per their isDark flag;
 * the multi-palette picker is deferred. Black (id 2) = dark Blue with pure-black surfaces.
 */
fun resolveColorScheme(isDark: Boolean, isBlack: Boolean): ColorScheme = when {
    isBlack -> BlueBlackColors
    isDark -> BlueDarkColors
    else -> BlueLightColors
}

/**
 * The exact [ColorScheme] [AppTheme] resolves — dynamic color (Material You) on API >= 31, else the
 * brand Blue scheme from [resolveColorScheme]. Not @Composable, so it can also be read off-composition
 * (e.g. to tint a dialog window's background to match the hosted Compose content). Reads [Themes]
 * statically; a theme change still flows through the existing Activity-recreate path.
 */
fun appColorScheme(context: Context): ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val dynamic = if (Themes.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (Themes.isBlack) dynamic.forceBlack() else dynamic  // same override as the static path
    }
    else -> resolveColorScheme(Themes.isDark, Themes.isBlack)
}

/**
 * The single Compose theme entry point.
 * Structured Expressive-ready: swap MaterialTheme -> MaterialExpressiveTheme when material3 1.5.0 is stable.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colorScheme = appColorScheme(LocalContext.current)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        // Provide a themed Surface so the hosted content gets colorScheme.background +
        // onBackground as LocalContentColor (bare Text() would otherwise default to black).
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
