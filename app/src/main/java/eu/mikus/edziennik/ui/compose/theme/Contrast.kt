/*
 * Copyright (c) Mikolaj Olszewski 2026-9-6.
 */

package eu.mikus.edziennik.ui.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/**
 * WCAG 2.x contrast ratio between two opaque colours, in `[1, 21]`.
 * [Color.luminance] is already WCAG relative luminance, so this is only the ratio formula.
 */
fun Color.contrastRatio(other: Color): Float {
    val hi = maxOf(luminance(), other.luminance())
    val lo = minOf(luminance(), other.luminance())
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * Blend towards [target] by [t], **per channel in sRGB**.
 *
 * Deliberately not `androidx.compose.ui.graphics.lerp`, which converts to Oklab first: the ramp
 * offsets in `Color.kt` and every expected value in this phase were derived with sRGB arithmetic.
 */
fun Color.blendTowards(target: Color, t: Float): Color = Color(
    red = red + (target.red - red) * t,
    green = green + (target.green - green) * t,
    blue = blue + (target.blue - blue) * t,
    alpha = alpha,
)

/**
 * The colour on the line from [surface] towards [ink] (or, for a negative [t], towards the opposite
 * pole) whose **relative luminance** is `L(surface) + t * (L(ink) - L(surface))`.
 *
 * The surface-family offsets in `Color.kt` are fractions of a *luminance* span, not blend fractions.
 * Feeding them straight into [blendTowards] is a unit error that yields a family roughly twice too
 * strong on light themes and four times too weak on dark ones - and monotonicity still holds, so it
 * does not show up in the obvious test. See `ColorSchemeDerivationTest`'s round-trip.
 */
fun solveForLuminance(surface: Color, ink: Color, t: Float): Color {
    val ls = surface.luminance()
    val li = ink.luminance()
    val target = ls + t * (li - ls)
    val pole = when {
        t >= 0f -> ink
        li < ls -> Color.White
        else -> Color.Black
    }
    val lp = pole.luminance()
    // A zero offset means "this role IS the surface" - DarkRamp.dim and LightRamp.bright both use
    // it. Without this the strict crossing test below converges one 8-bit step past the surface.
    if (abs(target - ls) < EPSILON) return surface
    if (abs(lp - ls) < EPSILON) return surface

    var lo = 0f
    var hi = 1f
    repeat(ITERATIONS) {
        val mid = (lo + hi) / 2f
        val candidate = surface.blendTowards(pole, mid).luminance()
        if ((lp > ls) == (candidate > target)) hi = mid else lo = mid
    }
    return surface.blendTowards(pole, hi)
}

/**
 * The nearest colour to this one, moving only towards black or white, that clears [minRatio] against
 * **every** colour in [grounds].
 *
 * Always succeeds for a non-empty [grounds]: white clears the default 4.5:1 whenever a ground's
 * relative luminance is at most `1.05 / minRatio - 0.05`, black whenever it is at least
 * `0.05 * minRatio - 0.05`, and for 4.5 those ranges (<= 0.1833 and >= 0.175) overlap, so every
 * possible ground has a pole that works. For a [minRatio] above about 4.6 they no longer overlap,
 * which is why the postcondition below is an error and not a comment.
 *
 * Each candidate is a constructed [Color], so it is tested at the 8-bit precision Compose stores.
 */
fun Color.withMinContrast(grounds: List<Color>, minRatio: Float = 4.5f): Color {
    if (grounds.isEmpty()) return this
    val worst = grounds.minByOrNull { contrastRatio(it) } ?: return this
    if (contrastRatio(worst) >= minRatio) return this

    val pole = if (worst.luminance() <= 1.05f / minRatio - 0.05f) Color.White else Color.Black
    var lo = 0f
    var hi = 1f
    repeat(ITERATIONS) {
        val mid = (lo + hi) / 2f
        val candidate = blendTowards(pole, mid)
        if (grounds.all { candidate.contrastRatio(it) >= minRatio }) hi = mid else lo = mid
    }
    val result = blendTowards(pole, hi)
    val achieved = grounds.minOf { result.contrastRatio(it) }
    if (achieved < minRatio) {
        error("no pole reaches $minRatio for $this against $grounds (best $achieved)")
    }
    return result
}

private const val ITERATIONS = 40
private const val EPSILON = 1e-6f
