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
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        dateUnknown: Boolean = false,
    ): AttendanceFull = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.date } returns date
        every { this@mockk.baseType } returns baseType
        every { typeObject } returns type
        every { this@mockk.semester } returns semester
        every { this@mockk.seen } returns seen
        every { this@mockk.dateUnknown } returns dateUnknown
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

    // --- backlog item 50: duplicate LazyColumn key crash -------------------------------
    //
    // AttendanceType is a data class, so grouping by it compares all seven fields, while
    // NodeKey.TypeKey.stableId compares only id, typeName and typeSymbol. Two types that
    // differ solely in typeShort or typeColor therefore become two groups sharing one key,
    // and LazyColumn throws IllegalArgumentException. A school defining two flavours of the
    // same type is one row to the user, so the grouping narrows to match the key.

    /** Same (id, name, symbol) as [absentType]; differs only in short label and colour. */
    private val absentVariant =
        AttendanceType(1, 1, Attendance.TYPE_ABSENT, "nieobecno\u015b\u0107", "n", "nb", 0xFFFF0000.toInt())

    @Test
    fun `types differing only in short label and colour collapse into one row`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1), type = absentType),
                row(2, Date(2026, 6, 2), type = absentVariant),
            ),
            config, Period.ALL,
        )
        val types = types(state).types
        assertEquals(1, types.size)
        assertEquals(2, types.single().yearCount)
    }

    @Test
    fun `every type row has a unique key`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1), type = absentType),
                row(2, Date(2026, 6, 2), type = absentVariant),
                row(3, Date(2026, 6, 3), baseType = Attendance.TYPE_PRESENT, type = presentType),
            ),
            config, Period.ALL,
        )
        val ids = types(state).types.map { it.key.stableId }
        assertEquals(ids.size, ids.distinct().size, "duplicate LazyColumn keys: $ids")
    }

    // --- an attendance whose date the school did not give -------------------------------
    //
    // The Librus parser files such a row at 2019-01-01 and sets Attendance.dateUnknown; the flag,
    // not the date, says the row is undated, because a school can really send 2019-01-01. The
    // builder groups by date twice (Days by day, Months by year and month), so every undated row
    // must be kept out of those groupings and given one bucket of its own on each tab, appended
    // last and only when it has rows.

    /** The date the parser files an undated attendance under. A fresh instance: Date is mutable. */
    private fun filingDate() = Date(2019, 1, 1)

    private fun undated(
        id: Long,
        baseType: Int = Attendance.TYPE_ABSENT,
        type: AttendanceType = absentType,
        seen: Boolean = true,
    ) = row(id, filingDate(), baseType = baseType, type = type, seen = seen, dateUnknown = true)

    private fun List<DayRangeHeader>.leafIds() = map { range -> range.leaves.map { it.attendance.id } }

    @Test
    fun `undated rows form their own Days bucket, last`() {
        val state = AttendanceTreeBuilder.build(
            // the undated row sits between the real ones, so "last" is the builder's doing, not the input order
            listOf(row(1, Date(2026, 6, 1)), undated(2), row(3, Date(2026, 5, 1))),
            config, Period.ALL,
        )
        val ranges = days(state).dayRanges
        assertEquals(3, ranges.size)
        assertEquals(listOf(listOf(1L), listOf(3L), listOf(2L)), ranges.leafIds())
        assertEquals(NodeKey.UnknownDayKey, ranges.last().key)
        assertTrue(ranges.last().dateUnknown)
        assertEquals(filingDate(), ranges.last().rangeStart)
        assertEquals(filingDate(), ranges.last().rangeEnd)
        assertTrue(ranges.dropLast(1).none { it.dateUnknown })
        assertTrue(ranges.dropLast(1).all { it.key is NodeKey.DayRangeKey })
    }

    @Test
    fun `undated rows form their own Months bucket, last`() {
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1)), undated(2), row(3, Date(2026, 5, 1))),
            config, Period.ALL,
        )
        val months = months(state).months
        assertEquals(3, months.size)
        assertEquals(listOf(listOf(1L), listOf(3L), listOf(2L)), months.map { m -> m.leaves.map { it.attendance.id } })
        assertEquals(NodeKey.UnknownMonthKey, months.last().key)
        assertTrue(months.last().dateUnknown)
        assertTrue(months.dropLast(1).none { it.dateUnknown })
        assertTrue(months.dropLast(1).all { it.key is NodeKey.MonthKey })
    }

    @Test
    fun `the undated Months bucket has a null percentage but populated counts`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1), baseType = Attendance.TYPE_PRESENT, type = presentType),
                row(2, Date(2026, 6, 2)),
                undated(3, baseType = Attendance.TYPE_PRESENT, type = presentType),
                undated(4),
            ),
            config, Period.ALL,
        )
        val (june, unknown) = months(state).months
        // the same mix of types in a real month is a rate, so the null below is the builder's doing
        assertEquals(0.5f, june.percentage!!, 0.0001f)
        assertNull(unknown.percentage)
        assertEquals(2, unknown.counts.byType.sumOf { it.count })
    }

    @Test
    fun `the undated Months bucket carries the filing date's year and month`() {
        val unknown = months(AttendanceTreeBuilder.build(listOf(undated(1)), config, Period.ALL)).months.single()
        assertEquals(NodeKey.UnknownMonthKey, unknown.key)   // the year and month below are the unknown bucket's own
        // never read - every render site branches on dateUnknown first - but defined, so a stray
        // "1.2019" on screen cannot be mistaken for a deliberate label
        assertEquals(2019, unknown.year)
        assertEquals(1, unknown.month)
    }

    @Test
    fun `an undated row is not merged into an adjacent day range`() {
        // 2019-01-02 is one day from the filing date, so a merge that could see the undated row
        // would fold the two into a single run and the undated row would read as a real day
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2019, 1, 2)), undated(2)),
            config.copy(groupConsecutiveDays = true), Period.ALL,
        )
        val ranges = days(state).dayRanges
        assertEquals(2, ranges.size)
        assertEquals(NodeKey.DayRangeKey(20190102L), ranges.first().key)
        assertEquals(NodeKey.UnknownDayKey, ranges.last().key)
        assertEquals(listOf(listOf(1L), listOf(2L)), ranges.leafIds())
        // the real day stays a single day; a run stretched back to the filing date would read 1.1.2019 - 2.1.2019
        assertEquals(Date(2019, 1, 2), ranges.first().rangeStart)
        assertEquals(Date(2019, 1, 2), ranges.first().rangeEnd)
    }

    @Test
    fun `an unseen undated row marks both unknown buckets unseen`() {
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1)), undated(2, seen = false)),
            config, Period.ALL,
        )
        assertTrue(days(state).dayRanges.last().hasUnseen)
        assertTrue(months(state).months.last().hasUnseen)
        assertFalse(days(state).dayRanges.first().hasUnseen)     // the real day is seen, so the flag is the undated row's own
        assertFalse(months(state).months.first().hasUnseen)
    }

    @Test
    fun `the unknown keys do not collide with a real 2019-01-01 bucket`() {
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2019, 1, 1)), undated(2)),
            config, Period.ALL,
        )
        val ranges = days(state).dayRanges
        val dayIds = ranges.map { it.key.stableId }
        assertEquals(listOf(NodeKey.DayRangeKey(20190101L), NodeKey.UnknownDayKey), ranges.map { it.key })
        assertEquals(dayIds.size, dayIds.distinct().size, "duplicate LazyColumn keys: $dayIds")
        assertEquals(listOf(listOf(1L), listOf(2L)), ranges.leafIds())

        val months = months(state).months
        val monthIds = months.map { it.key.stableId }
        assertEquals(listOf(NodeKey.MonthKey(2019, 1), NodeKey.UnknownMonthKey), months.map { it.key })
        assertEquals(monthIds.size, monthIds.distinct().size, "duplicate LazyColumn keys: $monthIds")
    }

    @Test
    fun `no undated rows means no unknown bucket on either tab`() {
        val state = AttendanceTreeBuilder.build(
            listOf(
                row(1, Date(2026, 6, 1)),
                row(2, Date(2026, 5, 2), baseType = Attendance.TYPE_PRESENT, type = presentType),
            ),
            config, Period.ALL,
        )
        // this is every existing install the day the column ships: the undated partition is empty,
        // and an unconditional append would show an empty "date unknown" header on both tabs
        val ranges = days(state).dayRanges
        assertEquals(listOf<NodeKey>(NodeKey.DayRangeKey(20260601L)), ranges.map { it.key })
        assertTrue(ranges.all { it.leaves.isNotEmpty() })
        assertEquals(
            listOf<NodeKey>(NodeKey.MonthKey(2026, 6), NodeKey.MonthKey(2026, 5)),
            months(state).months.map { it.key },
        )
    }

    @Test
    fun `an all-present undated set adds a Months bucket and no Days bucket`() {
        // Days drops PRESENT before grouping and Months does not, so the same undated set is empty
        // for one tab and not for the other; each tab needs its own guard
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1)), undated(2, baseType = Attendance.TYPE_PRESENT, type = presentType)),
            config, Period.ALL,    // showPresenceInMonth = false
        )
        assertEquals(listOf<NodeKey>(NodeKey.DayRangeKey(20260601L)), days(state).dayRanges.map { it.key })

        val months = months(state).months
        assertEquals(listOf<NodeKey>(NodeKey.MonthKey(2026, 6), NodeKey.UnknownMonthKey), months.map { it.key })
        val unknown = months.last()
        assertTrue(unknown.leaves.isEmpty())                       // PRESENT dropped from the leaves
        assertEquals(1, unknown.counts.byType.sumOf { it.count })  // but still counted
        assertTrue(unknown.dateUnknown)                            // empty leaves must not read as a real month
    }

    /**
     * Types and List never read the date, so an undated row is a real attendance on both and must
     * keep counting. Summary's half of this property is pinned in `AttendanceSummaryAggregatorTest`;
     * without this one, a change that dropped undated rows from the tree's own two date-blind tabs
     * would leave the whole suite green - and preserving these counts is the reason this phase kept
     * the record instead of skipping it.
     */
    @Test
    fun `an undated row still counts in Types and appears in List`() {
        val state = AttendanceTreeBuilder.build(
            listOf(row(1, Date(2026, 6, 1)), undated(2)),
            config, Period.ALL,
        )

        val absent = types(state).types.single { it.type.baseType == Attendance.TYPE_ABSENT }
        assertEquals(2, absent.yearCount)                                     // dated and undated alike
        assertEquals(2, absent.semesterCount)                                 // semester is the API's, not the date's
        assertEquals(listOf(1L, 2L), absent.leaves.map { it.attendance.id }.sorted())

        assertEquals(listOf(1L, 2L), list(state).leaves.map { it.attendance.id }.sorted())
    }
}
