/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class Segment(val color: Color, val count: Int)

@Composable
fun AttendanceBarChart(
    segments: List<Segment>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 14.sp)
    val pad8 = with(density) { 8.dp.toPx() }
    val pad2 = with(density) { 2.dp.toPx() }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        val sum = segments.sumOf { it.count }
        if (sum == 0) return@Canvas
        val unitWidth = size.width / sum.toFloat()
        var left = 0f
        for (seg in segments) {
            if (seg.count == 0) continue
            val w = unitWidth * seg.count
            drawRect(color = seg.color, topLeft = Offset(left, 0f), size = Size(w, size.height))

            val label = "${(100f * seg.count / sum).roundToInt()}%"
            val measured = textMeasurer.measure(AnnotatedString(label), labelStyle)
            val tw = measured.size.width
            val th = measured.size.height
            if (w > tw + pad8 && size.height > th + pad2) {
                drawText(
                    textLayoutResult = measured,
                    color = legibleTextColor(seg.color),
                    topLeft = Offset(left + w / 2f - tw / 2f, size.height / 2f - th / 2f),
                )
            }
            left += w
        }
    }
}
