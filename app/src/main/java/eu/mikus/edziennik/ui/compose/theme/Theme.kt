/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose.theme

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
 * The single Compose theme entry point. Dynamic color (Material You) on API >= 31,
 * else the brand Blue scheme from [resolveColorScheme]. Reads [Themes] at composition
 * time; a theme change still flows through the existing Activity-recreate path.
 * Structured Expressive-ready: swap MaterialTheme -> MaterialExpressiveTheme when material3 1.5.0 is stable.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val isDark = Themes.isDark
    val isBlack = Themes.isBlack
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (isBlack) dynamic.forceBlack() else dynamic  // same override as the static path
        }
        else -> resolveColorScheme(isDark, isBlack)
    }
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
