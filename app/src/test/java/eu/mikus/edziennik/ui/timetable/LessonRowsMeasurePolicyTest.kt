/*
 * Copyright (c) Mikolaj Olszewski 2026-11-10.
 */

package eu.mikus.edziennik.ui.timetable

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [lessonRowsMeasurePolicy], the layout that stacks a lesson block's rows. It must behave like the
 * `Column` it replaced — each row gets only the height the rows above it left — and additionally narrow,
 * by the event dots' width, exactly the rows that reach into the dots' strip at the bottom of the block.
 *
 * Density is 1, so 1 dp == 1 px. The row heights are illustrative, in the order a cancelled block has its
 * rows: a 38 px top row, an 18 px annotation chip, a 20 px time line and an 18 px teacher row. They are
 * chosen to put the strip boundary inside a known row, not measured from the real composables.
 */
class LessonRowsMeasurePolicyTest {

    private val scope = object : MeasureScope {
        override val density = 1f
        override val fontScale = 1f
        override val layoutDirection = LayoutDirection.Ltr
    }

    private class FakePlaceable(width: Int, height: Int) : Placeable() {
        var placedAt: IntOffset? = null

        init {
            measuredSize = IntSize(width, height)
        }

        override fun get(alignmentLine: AlignmentLine) = AlignmentLine.Unspecified

        override fun placeAt(position: IntOffset, zIndex: Float, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
            placedAt = position
        }
    }

    /** A row whose natural height is [natural] at any width; it records how it was measured. */
    private class FakeRow(private val natural: Int) : Measurable {
        var constraints: Constraints? = null
        var placeable: FakePlaceable? = null
        var intrinsicQueries = 0
        val intrinsicWidths = mutableListOf<Int>()

        override val parentData: Any? = null

        override fun measure(constraints: Constraints): Placeable {
            this.constraints = constraints
            return FakePlaceable(constraints.maxWidth, natural.coerceAtMost(constraints.maxHeight))
                .also { placeable = it }
        }

        override fun minIntrinsicHeight(width: Int): Int = natural.also { intrinsicQueries++; intrinsicWidths += width }
        override fun maxIntrinsicHeight(width: Int): Int = natural.also { intrinsicQueries++; intrinsicWidths += width }
        override fun minIntrinsicWidth(height: Int): Int = 0
        override fun maxIntrinsicWidth(height: Int): Int = 0
    }

    private fun layOut(policy: MeasurePolicy, rows: List<FakeRow>, width: Int, height: Int) {
        val result = with(policy) { scope.measure(rows, Constraints.fixed(width, height)) }
        assertEquals(width, result.width)
        assertEquals(height, result.height)
        result.placeChildren()
    }

    private fun policy(events: Int): MeasurePolicy = lessonRowsMeasurePolicy(dotsWidth = 14.dp * events, dotsHeight = 10.dp)

    private fun widths(rows: List<FakeRow>) = rows.map { it.constraints?.maxWidth }
    private fun maxHeights(rows: List<FakeRow>) = rows.map { it.constraints?.maxHeight }
    private fun tops(rows: List<FakeRow>) = rows.map { it.placeable?.placedAt?.y }

    private fun cancelledBlockRows() = listOf(FakeRow(38), FakeRow(18), FakeRow(20), FakeRow(18))

    @Test
    fun noEvents_layOutAsAColumn_fullWidth_noIntrinsicQuery() {
        val rows = cancelledBlockRows()
        layOut(policy(events = 0), rows, width = 100, height = 70)

        assertEquals(listOf(100, 100, 100, 100), widths(rows))
        assertEquals(listOf(70, 32, 14, 0), maxHeights(rows))
        assertEquals(listOf(0, 38, 56, 70), tops(rows))
        assertEquals(0, rows.sumOf { it.intrinsicQueries })
    }

    @Test
    fun tallBlock_rowsEndAboveTheStrip_nothingNarrowed() {
        val rows = cancelledBlockRows() // ends at 94, strip starts at 110
        layOut(policy(events = 3), rows, width = 100, height = 120)

        assertEquals(listOf(100, 100, 100, 100), widths(rows))
        assertEquals(listOf(0, 38, 56, 76), tops(rows))
    }

    @Test
    fun overflowingBlock_narrowsExactlyTheRowsReachingTheStrip() {
        val rows = cancelledBlockRows() // strip starts at 60: the time line (56..76) and teacher row reach it
        layOut(policy(events = 3), rows, width = 100, height = 70)

        assertEquals(listOf(100, 100, 58, 58), widths(rows))
        assertEquals(listOf(70, 32, 14, 0), maxHeights(rows))
        assertEquals(listOf(0, 38, 56, 70), tops(rows))
        // Whether a row reaches the strip is judged from its height at the full width, once per row.
        assertEquals(listOf(listOf(100), listOf(100), listOf(100), listOf(100)), rows.map { it.intrinsicWidths })
    }

    @Test
    fun narrowsByTheDotsWidth_onePerEvent() {
        val rows = cancelledBlockRows()
        layOut(policy(events = 1), rows, width = 100, height = 70)

        assertEquals(listOf(100, 100, 86, 86), widths(rows))
    }

    @Test
    fun rowEndingExactlyAtTheStrip_keepsFullWidth() {
        val rows = listOf(FakeRow(40), FakeRow(20)) // second row ends at 60 == strip top
        layOut(policy(events = 2), rows, width = 100, height = 70)

        assertEquals(listOf(100, 100), widths(rows))
    }

    @Test
    fun veryShortBlock_narrowsTheTopRowToo() {
        val rows = cancelledBlockRows() // strip starts at 30, inside the 38 px top row
        layOut(policy(events = 2), rows, width = 100, height = 40)

        assertEquals(listOf(72, 72, 72, 72), widths(rows))
        assertEquals(listOf(40, 2, 0, 0), maxHeights(rows))
    }

    @Test
    fun dotsWiderThanTheBlock_narrowToZero_notNegative() {
        val rows = listOf(FakeRow(38), FakeRow(20))
        layOut(policy(events = 3), rows, width = 30, height = 40)

        assertEquals(listOf(0, 0), widths(rows))
    }
}
