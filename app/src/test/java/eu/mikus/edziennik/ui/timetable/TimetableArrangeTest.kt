/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.ui.timetable.TimetableArrange.LessonSpan
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TimetableArrangeTest {

    private fun span(start: Int, end: Int) = LessonSpan(start, end)

    /** column/columnCount per input index, for terse assertions. */
    private fun cols(vararg spans: LessonSpan): List<Pair<Int, Int>> =
        TimetableArrange.arrange(spans.toList()).map { it.column to it.columnCount }

    @Test
    fun `empty yields empty`() {
        assertEquals(emptyList(), TimetableArrange.arrange(emptyList()))
    }

    @Test
    fun `single lesson is column 0 of 1`() {
        assertEquals(listOf(0 to 1), cols(span(480, 525)))
    }

    @Test
    fun `non-overlapping sequence each full width`() {
        assertEquals(listOf(0 to 1, 0 to 1, 0 to 1), cols(span(480, 525), span(540, 585), span(600, 645)))
    }

    @Test
    fun `adjacent touching end equals start is NOT overlapping`() {
        // 8:00-8:45 then 8:45-9:30 -> two separate clusters, each full width
        assertEquals(listOf(0 to 1, 0 to 1), cols(span(480, 525), span(525, 570)))
    }

    @Test
    fun `simple pair overlap gets two columns`() {
        assertEquals(listOf(0 to 2, 1 to 2), cols(span(480, 525), span(500, 560)))
    }

    @Test
    fun `three-way parallel gets three columns`() {
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3), cols(span(480, 540), span(490, 550), span(500, 560)))
    }

    @Test
    fun `nested long plus two shorts share the cluster column count`() {
        // long 8:00-10:00, short 8:10-8:40, short 8:50-9:20 -> the two shorts reuse one column
        val result = cols(span(480, 600), span(490, 520), span(530, 560))
        assertEquals(listOf(0 to 2, 1 to 2, 1 to 2), result)
    }

    @Test
    fun `two separate clusters keep independent column counts`() {
        // cluster A: 2 overlapping; gap; cluster B: single
        assertEquals(
            listOf(0 to 2, 1 to 2, 0 to 1),
            cols(span(480, 540), span(490, 550), span(600, 660)),
        )
    }

    @Test
    fun `output preserves input order`() {
        // deliberately unsorted input
        val out = TimetableArrange.arrange(listOf(span(600, 660), span(480, 540), span(490, 550)))
        assertEquals(listOf(0, 1, 2), out.map { it.index })
    }
}
