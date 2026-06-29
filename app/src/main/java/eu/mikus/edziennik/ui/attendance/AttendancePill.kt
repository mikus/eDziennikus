/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single shared legible-text rule, reused by the pill and the bar-chart labels.
 * Matches the legacy AttendanceView / Phase-5 GradePill threshold.
 */
internal fun legibleTextColor(bg: Color): Color =
    if (bg.luminance() > 0.3f) Color(0xAA000000) else Color(0xCCFFFFFF)

/**
 * Stateless port of AttendanceView. The caller resolves the text (symbol vs short) and the colour,
 * so the pill stays free of AttendanceManager.
 */
@Composable
fun AttendancePill(
    text: String,
    color: Color,
    big: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (big) 8.dp else 4.dp)
    Text(
        text = text.ifBlank { "  " },
        color = legibleTextColor(color),
        fontSize = if (big) 22.sp else 14.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .background(color, shape)
            .padding(horizontal = if (big) 4.dp else 5.dp, vertical = if (big) 2.dp else 0.dp),
    )
}
