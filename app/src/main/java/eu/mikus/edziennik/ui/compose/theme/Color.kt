/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Translated from develop's app_blue_* M3 palette (colors_m3.xml). surfaceTint defaults to primary.
val BlueLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF00538C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0078C7), onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF9ECAFF),
    secondary = Color(0xFF446081), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC3DDFF), onSecondaryContainer = Color(0xFF274565),
    tertiary = Color(0xFF743190), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9C58B8), onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF181C21),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF181C21),
    surfaceVariant = Color(0xFFDCE3F0), onSurfaceVariant = Color(0xFF404752),
    surfaceTint = Color(0xFF00538C),
    inverseSurface = Color(0xFF2D3136), inverseOnSurface = Color(0xFFEEF1F8),
    outline = Color(0xFF707883), outlineVariant = Color(0xFFC0C7D3), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F9FF), surfaceDim = Color(0xFFD7DAE1),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F3FB),
    surfaceContainer = Color(0xFFEBEEF5), surfaceContainerHigh = Color(0xFFE5E8F0),
    surfaceContainerHighest = Color(0xFFE0E2EA),
)

val BlueDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0076C4), onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF0061A3),
    secondary = Color(0xFFABC9EF), onSecondary = Color(0xFF113251),
    secondaryContainer = Color(0xFF234161), onSecondaryContainer = Color(0xFFB9D7FD),
    tertiary = Color(0xFFEBB2FF), onTertiary = Color(0xFF51066E),
    tertiaryContainer = Color(0xFF9C57B8), onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101419), onBackground = Color(0xFFE0E2EA),
    surface = Color(0xFF101419), onSurface = Color(0xFFE0E2EA),
    surfaceVariant = Color(0xFF404752), onSurfaceVariant = Color(0xFFC0C7D3),
    surfaceTint = Color(0xFF9ECAFF),
    inverseSurface = Color(0xFFE0E2EA), inverseOnSurface = Color(0xFF2D3136),
    outline = Color(0xFF8A919D), outlineVariant = Color(0xFF404752), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF36393F), surfaceDim = Color(0xFF101419),
    surfaceContainerLowest = Color(0xFF0B0E13), surfaceContainerLow = Color(0xFF181C21),
    surfaceContainer = Color(0xFF1C2025), surfaceContainerHigh = Color(0xFF262A30),
    surfaceContainerHighest = Color(0xFF31353B),
)

// Black/AMOLED override: force the background + the whole surface/container family to
// (near-)black. ONE definition, applied to BOTH the static brand scheme (below) and the
// API 31+ dynamic scheme (Theme.kt) so the two paths cannot drift. (Mirrors the fork's
// AppTheme.Black overriding android:colorBackground/colorSurface.)
fun ColorScheme.forceBlack(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF0B0E13),
    surfaceContainerHighest = Color(0xFF101419),
)

// Black/AMOLED is synthesized from the dark Blue scheme via the shared override above.
val BlueBlackColors: ColorScheme = BlueDarkColors.forceBlack()
