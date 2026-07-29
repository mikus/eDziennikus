/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.compat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.elevation.ElevationOverlayProvider
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.colorInt

/**
 * Local reimplementations of pl.szczodrzynski.navlib's pure color/drawable utilities (its `UtilsKt`),
 * so non-shell code no longer depends on navlib. Behavior is matched to navlib 0.8.0's bytecode; the
 * signatures + call shapes are identical, so the migration is an import-swap. navlib itself is not
 * removed here — the shell (MainActivity) and ImageHolder still use it until later shell-swap phases.
 *
 * Kept in its own package (not `ext`) because MainActivity wildcard-imports both `ext.*` and
 * `navlib.*`; sharing a package would make these names ambiguous with navlib's in the shell.
 */

/** navlib: resolve a theme color attr to a color int. */
fun getColorFromAttr(context: Context, @AttrRes attr: Int): Int =
    TypedValue().also { context.theme.resolveAttribute(attr, it, true) }.data

/** navlib: theme-aware drawable lookup (non-null, like navlib's). */
fun Context.getDrawableFromRes(@DrawableRes res: Int): Drawable =
    ContextCompat.getDrawable(this, res)!!

/** navlib: the Material elevation-overlay surface color at [dp] elevation. */
fun elevateSurface(context: Context, dp: Int): Int {
    val provider = ElevationOverlayProvider(context)
    val elevationPx = dp * context.resources.displayMetrics.density
    return provider.compositeOverlay(provider.themeSurfaceColor, elevationPx)
}

/**
 * navlib: alpha-composite [color2] over [color1] using color2's alpha as the blend ratio,
 * returning an opaque color (result alpha forced to 0xFF).
 */
fun blendColors(color1: Int, color2: Int): Int {
    val ratio = Color.alpha(color2) / 255f
    val inv = 1f - ratio
    val r = (Color.red(color1) * inv + Color.red(color2) * ratio).toInt()
    val g = (Color.green(color1) * inv + Color.green(color2) * ratio).toInt()
    val b = (Color.blue(color1) * inv + Color.blue(color2) * ratio).toInt()
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}

/** navlib: tint an IconicsDrawable by a theme color attr (mutates, like navlib's). */
fun IconicsDrawable.colorAttr(context: Context, @AttrRes attr: Int) {
    colorInt = getColorFromAttr(context, attr)
}
