/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.utils.models.Time
import kotlinx.coroutines.delay

private val GutterWidth = 40.dp
private val GutterMarginEnd = 10.dp

/**
 * A day time-grid: a fixed-height custom [Layout] (not lazy — absolute positioning needs a Layout)
 * inside a [verticalScroll]. Draws the hour gutter + dividers behind the placed [LessonBlock]s, plus
 * a live "now" line when [isToday]. The per-minute height is `lessonHeight.dp / 30`
 * (lessonHeight = per-half-hour dp from the UI config).
 */
@Composable
fun TimetableGrid(
    startHour: Int,
    endHour: Int,
    lessonHeight: Int,
    blocks: List<PositionedLesson>,
    isToday: Boolean,
    colorSubjectName: Boolean,
    onLessonClick: (PositionedLesson) -> Unit,
    attendanceIconFactory: (Context, AttendanceFull) -> Drawable?,
    modifier: Modifier = Modifier,
) {
    val minuteHeight: Dp = lessonHeight.dp / 30f
    val totalMinutes = (endHour - startHour) * 60
    val hourDivider = MaterialTheme.colorScheme.outline
    val halfHourDivider = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nowColor = MaterialTheme.colorScheme.error
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // now-line: minute-of-day, refreshed every 30s while today & in range
    var nowMinute by remember { mutableIntStateOf(Time.getNow().inMinutes) }
    if (isToday) {
        LaunchedEffect(Unit) {
            while (true) {
                nowMinute = Time.getNow().inMinutes
                delay(30_000L)
            }
        }
    }

    // initial scroll to the first lesson's top
    LaunchedEffect(blocks, minuteHeight) {
        val firstTop = blocks.minOfOrNull { it.startMinute }
        if (firstTop != null) {
            val px = with(density) { ((firstTop - startHour * 60) * minuteHeight.toPx()).toInt() }
            scrollState.scrollTo(px.coerceAtLeast(0))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        Layout(
            content = {
                blocks.forEach { block ->
                    LessonBlock(
                        block = block,
                        colorSubjectName = colorSubjectName,
                        onClick = { onLessonClick(block) },
                        attendanceIconFactory = attendanceIconFactory,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .drawGrid(
                    startHour = startHour,
                    endHour = endHour,
                    minuteHeight = minuteHeight,
                    gutterWidth = GutterWidth,
                    hourColor = hourDivider,
                    halfHourColor = halfHourDivider,
                    nowMinute = if (isToday && nowMinute in startHour * 60..endHour * 60) nowMinute else null,
                    nowColor = nowColor,
                    labelStyle = labelStyle,
                    textMeasurer = textMeasurer,
                ),
        ) { measurables, constraints ->
            val gutterPx = with(density) { (GutterWidth + GutterMarginEnd).roundToPx() }
            val totalWidth = constraints.maxWidth
            val gridWidth = (totalWidth - gutterPx).coerceAtLeast(0)
            val heightPx = with(density) { (totalMinutes * minuteHeight.toPx()).toInt() }.coerceAtLeast(0)

            val placeables = measurables.mapIndexed { i, m ->
                val block = blocks[i]
                val colW = if (block.columnCount > 0) gridWidth / block.columnCount else gridWidth
                val h = with(density) {
                    ((block.endMinute - block.startMinute) * minuteHeight.toPx()).toInt()
                }.coerceAtLeast(0)
                m.measure(Constraints.fixed(colW.coerceAtLeast(0), h)) to block
            }

            layout(totalWidth, heightPx) {
                placeables.forEach { (p, block) ->
                    val colW = if (block.columnCount > 0) gridWidth / block.columnCount else gridWidth
                    val x = gutterPx + block.column * colW
                    val y = with(density) {
                        ((block.startMinute - startHour * 60) * minuteHeight.toPx()).toInt()
                    }
                    p.place(x, y)
                }
            }
        }
    }
}

/** Draws hour labels + hour/half-hour dividers + optional now-line behind the block layer. */
private fun Modifier.drawGrid(
    startHour: Int,
    endHour: Int,
    minuteHeight: Dp,
    gutterWidth: Dp,
    hourColor: Color,
    halfHourColor: Color,
    nowMinute: Int?,
    nowColor: Color,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
): Modifier = this.drawBehind {
    val minutePx = minuteHeight.toPx()
    val gutterPx = gutterWidth.toPx()
    for (h in startHour..endHour) {
        val y = (h - startHour) * 60 * minutePx
        drawLine(
            hourColor,
            start = Offset(gutterPx, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
        if (h < endHour) {
            val yHalf = y + 30 * minutePx
            drawLine(
                halfHourColor,
                start = Offset(gutterPx, yHalf),
                end = Offset(size.width, yHalf),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val label = textMeasurer.measure("$h:00", labelStyle)
        drawText(label, topLeft = Offset(0f, y))
    }
    if (nowMinute != null) {
        val y = (nowMinute - startHour * 60) * minutePx
        drawLine(
            nowColor,
            start = Offset(gutterPx, y),
            end = Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
