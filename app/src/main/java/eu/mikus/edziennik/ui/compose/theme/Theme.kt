/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose.theme

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.mikus.edziennik.utils.Themes

/**
 * The exact [ColorScheme] [AppTheme] resolves, derived from the theme the user picked in settings.
 * Not @Composable, so it can also be read off-composition (e.g. to tint a dialog window's background
 * to match the hosted Compose content). Reads [Themes] statically; a theme change still flows through
 * the existing Activity-recreate path.
 *
 * [context] is unused today. It is kept because `ComposeDialog.kt` calls this off-composition with an
 * Activity, and because a future system-palette picker entry (spec 6.1) would need it back.
 */
@Suppress("UNUSED_PARAMETER")
fun appColorScheme(context: Context, forceLight: Boolean = false): ColorScheme =
    schemeFor(themeId = Themes.theme.id, forceLight = forceLight)

@Composable
fun AppTheme(forceLight: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = appColorScheme(LocalContext.current, forceLight)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
