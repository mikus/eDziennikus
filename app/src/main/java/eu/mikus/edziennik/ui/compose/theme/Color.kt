/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import eu.mikus.edziennik.utils.Themes

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

/**
 * `(android:colorBackground, colorSurface)` per theme id, mirroring the `AppTheme.*` styles.
 *
 * Rows 2-17 come from `res/values/colors.xml`'s `windowBackground*`/`colorSurface*` pairs. **Rows 0
 * and 1 come from `res/values/styles.xml:146-147` and `:222-223`**, where `AppTheme.Light`/`.Dark`
 * set `colorSurface` inline; the `colorSurfaceLight`/`colorSurfaceDark` resources are declared but
 * never referenced, so reading them here would pin a value nothing else uses.
 *
 * No default: an id with no row must fail loudly, or a nineteenth theme added later would silently
 * revert to the Phase 0 behaviour this phase exists to remove.
 */
val ThemeSurfaces: Map<Int, Pair<Color, Color>> = mapOf(
    0 to (Color(0xFFFFFFFF) to Color(0xFFFFFFFF)),   // Light
    1 to (Color(0xFF242424) to Color(0xFF333333)),   // Dark
    2 to (Color(0xFF000000) to Color(0xFF121212)),   // Black
    3 to (Color(0xFF3E2723) to Color(0xFF4B3632)),   // Chocolate
    4 to (Color(0xFF1A237E) to Color(0xFF2A3287)),   // Indigo
    5 to (Color(0xFFFFFFC9) to Color(0xFFFFFFD9)),   // LightYellow
    6 to (Color(0xFF0F2833) to Color(0xFF1F3741)),   // DarkBlue
    7 to (Color(0xFF0D47A1) to Color(0xFF1E53A7)),   // Blue
    8 to (Color(0xFFC9ECFC) to Color(0xFFD9F1FC)),   // LightBlue
    9 to (Color(0xFF2F0F34) to Color(0xFF3D1F42)),   // DarkPurple
    10 to (Color(0xFF7B1FA2) to Color(0xFF842EA8)),  // Purple
    11 to (Color(0xFFF6C9FD) to Color(0xFFF8D9FD)),  // LightPurple
    12 to (Color(0xFF350F0F) to Color(0xFF431F1F)),  // DarkRed
    13 to (Color(0xFFFF5252) to Color(0xFFFF5E5E)),  // Red
    14 to (Color(0xFFF6C9C9) to Color(0xFFF8D9D9)),  // LightRed
    15 to (Color(0xFF0F2D1B) to Color(0xFF1F3B2B)),  // DarkGreen
    16 to (Color(0xFFFFD54F) to Color(0xFFFFD75B)),  // Amber
    17 to (Color(0xFFEDFAC9) to Color(0xFFF2FBD9)),  // LightGreen
)

/**
 * How far each surface-family role sits from `surface` towards the ink, as a fraction of the
 * `surface -> onSurface` **relative-luminance** span. Measured from [BlueLightColors] and
 * [BlueDarkColors] themselves, so a theme with no ramp of its own inherits Blue's *shape* while
 * taking its own hue. `containerLowest` is negative in both: it sits away from the ink.
 *
 * These are luminance fractions, NOT blend fractions - apply them through [solveForLuminance], never
 * through [blendTowards] directly. `ColorSchemeDerivationTest`'s round-trip is what enforces that.
 */
private class SurfaceRamp(
    val containerLowest: Float, val containerLow: Float, val container: Float,
    val containerHigh: Float, val containerHighest: Float,
    val dim: Float, val bright: Float, val variant: Float,
)

private val LightRamp = SurfaceRamp(-0.0541f, 0.0550f, 0.1015f, 0.1521f, 0.1999f, 0.2655f, 0.0000f, 0.1971f)
private val DarkRamp = SurfaceRamp(-0.0033f, 0.0060f, 0.0097f, 0.0212f, 0.0375f, 0.0000f, 0.0449f, 0.0732f)

/** Above this relative luminance a surface takes the light ramp and the light ink. */
private const val LIGHT_SURFACE_THRESHOLD = 0.1833f

/** Re-ground a scheme on [background]/[surface], carrying the whole surface family with it. */
fun ColorScheme.withSurfaces(background: Color, surface: Color): ColorScheme {
    val lightish = surface.luminance() >= LIGHT_SURFACE_THRESHOLD
    val ramp = if (lightish) LightRamp else DarkRamp
    val baseInk = if (lightish) BlueLightColors.onSurface else BlueDarkColors.onSurface
    val baseInkVariant =
        if (lightish) BlueLightColors.onSurfaceVariant else BlueDarkColors.onSurfaceVariant

    // Pass 1: shape the family from the undecorated ink, so the grounds are known.
    fun at(t: Float) = solveForLuminance(surface, baseInk, t)
    val lowest = at(ramp.containerLowest)
    val low = at(ramp.containerLow)
    val container = at(ramp.container)
    val high = at(ramp.containerHigh)
    val highest = at(ramp.containerHighest)
    val variant = at(ramp.variant)

    // Pass 2: lift the ink until it clears AA against every ground text is actually drawn on.
    val grounds = listOf(background, surface, lowest, low, container, high, highest, variant)
    return copy(
        background = background, onBackground = baseInk.withMinContrast(grounds),
        surface = surface, onSurface = baseInk.withMinContrast(grounds),
        surfaceVariant = variant, onSurfaceVariant = baseInkVariant.withMinContrast(grounds),
        surfaceContainerLowest = lowest, surfaceContainerLow = low, surfaceContainer = container,
        surfaceContainerHigh = high, surfaceContainerHighest = highest,
        surfaceDim = at(ramp.dim), surfaceBright = at(ramp.bright),
    )
}

/**
 * The [ColorScheme] for one theme id. Pure - no Context, no global state - so the parity gate can
 * walk all eighteen rows without driving the [Themes] singleton.
 *
 * The **base** scheme is selected by the theme's own `isDark` flag, so `primary` and the brand roles
 * never move. Only the ramp and the ink (inside [withSurfaces]) read measured luminance. Red is the
 * one theme where the two disagree, and selecting its base by luminance would move its `primary` for
 * no gain - identical surfaces, ink and contrast either way.
 *
 * [forceLight] is the login screen's path (`LoginActivity.kt:35`), corresponds to no XML style, and
 * short-circuits to the brand light scheme for every theme.
 */
fun schemeFor(themeId: Int, forceLight: Boolean = false): ColorScheme {
    if (forceLight) return BlueLightColors
    val (background, surface) = requireNotNull(ThemeSurfaces[themeId]) {
        "no surface pair for theme id $themeId"
    }
    val isDark = Themes.themeList.first { it.id == themeId }.isDark
    val scheme = (if (isDark) BlueDarkColors else BlueLightColors).withSurfaces(background, surface)
    // The Black theme is AMOLED: keep the three lowest containers at true black rather than letting
    // the ramp lift them off it. The gate does not force this - see the spec's 6.6.
    return if (themeId == BLACK_THEME_ID) {
        scheme.copy(
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF000000),
            surfaceContainer = Color(0xFF000000),
        )
    } else {
        scheme
    }
}

private const val BLACK_THEME_ID = 2
