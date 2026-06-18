/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.utils.colorInt

/**
 * Renders an Iconics [IIcon] (CommunityMaterial, SzkolnyFont, …) as a tinted Compose image. The
 * app's icon vocabulary lives in the Iconics fonts (no Compose vector resources), so all three
 * Phase-1 screens need this bridge. The icon is baked to a bitmap once (remembered per icon/tint/
 * size). Use for static icons only.
 */
@Composable
fun IconicsIcon(
    icon: IIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 24,
    tint: Color = LocalContentColor.current,
) {
    val context = LocalContext.current
    val argb = tint.toArgb()
    val sizePx = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    val painter = remember(icon, argb, sizePx) {
        BitmapPainter(
            IconicsDrawable(context, icon).apply { colorInt = argb }
                .toBitmap(width = sizePx, height = sizePx)
                .asImageBitmap()
        )
    }
    Image(painter = painter, contentDescription = contentDescription, modifier = Modifier.size(sizeDp.dp).then(modifier))
}
