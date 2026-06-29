/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.entity.AttendanceType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AttendanceTreeBuilderTest {

    private val presentType = AttendanceType(1, 0, Attendance.TYPE_PRESENT, "obecność", "ob", "•", null)
    private val absentType = AttendanceType(1, 1, Attendance.TYPE_ABSENT, "nieobecność", "nb", "nb", null)

    private fun row(
        id: Long,
        date: Date,
        baseType: Int = Attendance.TYPE_ABSENT,
        type: AttendanceType = absentType,
        semester: Int = 1,
        seen: Boolean = true,
    ): AttendanceFull = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.date } returns date
        every { this@mockk.baseType } returns baseType
        every { typeObject } returns type
        every { this@mockk.semester } returns semester
        every { this@mockk.seen } returns seen
        every { subjectId } returns 1L
        every { subjectLongName } returns "Algebra"
    }

    private val config = AttendanceTreeBuilder.Config(
        groupConsecutiveDays = false, showPresenceInMonth = false, currentSemester = 1,
    )

    private fun tabs(state: AttendanceUiState) = (state as AttendanceUiState.Content).tabs
    private fun days(state: AttendanceUiState) = tabs(state).filterIsInstance<AttendanceTab.DaysTab>().single()
    private fun months(state: AttendanceUiState) = tabs(state).filterIsInstance<AttendanceTab.MonthsTab>().single()
    private fun types(state: AttendanceUiState) = tabs(state).filterIsInstance<AttendanceTab.TypesTab>().single()
    private fun list(state: AttendanceUiState) = tabs(state).filterIsInstance<AttendanceTab.ListTab>().single()

    @Test
    fun `empty rows yield Empty`() {
        assertEquals(AttendanceUiState.Empty, AttendanceTreeBuilder.build(emptyList(), config, Period.ALL))
    }

    @Test
    fun `days drops PRESENT and groups by date when not merging`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1)),
                row(2, Date(2026, 6, 1)),
                row(3, Date(2026, 6, 5), baseType = Attendance.TYPE_PRESENT, type = presentType),
            ),
            config, Period.ALL,
        )
        val ranges = days(state).dayRanges
        assertEquals(1, ranges.size)                 // only the 2026-06-01 absences; the PRESENT day is dropped
        assertEquals(2, ranges.single().leaves.size)
    }

    @Test
    fun `days merges consecutive dates, NodeKey anchored on the range end`() {
        val merged = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1)), row(2, Date(2026, 6, 2)), row(3, Date(2026, 6, 3))),
            config.copy(groupConsecutiveDays = true), Period.ALL,
        )
        val range = days(merged).dayRanges.single()
        assertEquals(3, range.leaves.size)
        assertEquals(Date(2026, 6, 1).day, range.rangeStart.day)
        assertEquals(Date(2026, 6, 3).day, range.rangeEnd.day)
        assertEquals(NodeKey.DayRangeKey(2026L * 10000 + 6 * 100 + 3), range.key)  // anchor = END

        val unmerged = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1)), row(2, Date(2026, 6, 2)), row(3, Date(2026, 6, 3))),
            config.copy(groupConsecutiveDays = false), Period.ALL,
        )
        assertEquals(3, days(unmerged).dayRanges.size)
    }

    @Test
    fun `months group by year-month with counts and percentage, drop PRESENT when configured`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1), baseType = Attendance.TYPE_PRESENT, type = presentType),
                row(2, Date(2026, 6, 2)),
                row(3, Date(2026, 5, 2)),
            ),
            config, Period.ALL,    // showPresenceInMonth = false
        )
        val june = months(state).months.first { it.month == 6 }
        assertEquals(2, june.counts.byType.sumOf { it.count })  // counts include PRESENT
        assertEquals(1, june.leaves.size)                       // PRESENT dropped from leaves
        assertEquals(0.5f, june.percentage!!, 0.0001f)          // 1 present / 2 total
    }

    @Test
    fun `types use share-of-total percentage with year and semester counts`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1), baseType = Attendance.TYPE_PRESENT, type = presentType, semester = 1),
                row(2, Date(2026, 6, 2), type = absentType, semester = 1),
                row(3, Date(2026, 6, 3), type = absentType, semester = 2),
            ),
            config, Period.ALL,
        )
        val absent = types(state).types.first { it.type.baseType == Attendance.TYPE_ABSENT }
        assertEquals(2f / 3f, absent.sharePercent!!, 0.0001f)   // share of total rows
        assertEquals(2, absent.yearCount)
        assertEquals(1, absent.semesterCount)                  // only semester == 1
    }

    @Test
    fun `list is flat non-present leaves with captured unseen`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1), baseType = Attendance.TYPE_PRESENT, type = presentType),
                row(2, Date(2026, 6, 2), seen = false),
            ),
            config, Period.ALL,
        )
        val leaves = list(state).leaves
        assertEquals(1, leaves.size)
        assertTrue(leaves.single().unseen)
    }

    @Test
    fun `build delegates Summary and forwards the period`() {
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1), semester = 2)),
            config, Period.SEM1,
        )
        val summary = tabs(state).filterIsInstance<AttendanceTab.SummaryTab>().single()
        assertTrue(summary.subjects.isEmpty())  // the only row is semester 2, filtered out by SEM1
    }

    @Test
    fun `days merge breaks on a gap larger than one day`() {
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1)), row(2, Date(2026, 6, 2)), row(3, Date(2026, 6, 5))),
            config.copy(groupConsecutiveDays = true), Period.ALL,
        )
        val ranges = days(state).dayRanges
        assertEquals(2, ranges.size)                              // the gap splits 6/5 from 6/1–6/2
        val solo = ranges.first { it.rangeEnd.day == 5 }
        assertEquals(1, solo.leaves.size)
        assertEquals(5, solo.rangeStart.day)                     // single day: start == end
        val merged = ranges.first { it.rangeEnd.day == 2 }
        assertEquals(2, merged.leaves.size)
        assertEquals(1, merged.rangeStart.day)                   // oldest day of the run, not orphaned
    }

    @Test
    fun `days merge folds the last element of every consecutive run`() {
        // two separate 2-day runs: guards against regressing to the legacy last-element-drop on a non-terminal run
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1)), row(2, Date(2026, 6, 2)),
                row(3, Date(2026, 6, 5)), row(4, Date(2026, 6, 6)),
            ),
            config.copy(groupConsecutiveDays = true), Period.ALL,
        )
        val ranges = days(state).dayRanges
        assertEquals(2, ranges.size)
        val early = ranges.first { it.rangeEnd.day == 2 }
        assertEquals(2, early.leaves.size)
        assertEquals(1, early.rangeStart.day)     // oldest of run 1 folded in, not orphaned
        val late = ranges.first { it.rangeEnd.day == 6 }
        assertEquals(2, late.leaves.size)
        assertEquals(5, late.rangeStart.day)      // oldest of run 2 folded in, not orphaned
    }

    @Test
    fun `months keep PRESENT leaves when showPresenceInMonth is true`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1), baseType = Attendance.TYPE_PRESENT, type = presentType),
                row(2, Date(2026, 6, 2)),
            ),
            config.copy(showPresenceInMonth = true), Period.ALL,
        )
        val june = months(state).months.first { it.month == 6 }
        assertEquals(2, june.leaves.size)                        // PRESENT retained as a leaf
    }
}
