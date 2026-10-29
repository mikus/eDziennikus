/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.utils.models.Date

/**
 * Pure builder for the four list groupings (Days/Months/Types/List) over the full unfiltered set.
 * Delegates the Summary tab (which owns the period filter) to AttendanceSummaryAggregator.
 * Seam-free: reads entity fields directly; all colour/icon/symbol logic lives at the Screen edge.
 */
object AttendanceTreeBuilder {

    data class Config(
        val groupConsecutiveDays: Boolean,
        val showPresenceInMonth: Boolean,
        val currentSemester: Int,
    )

    fun build(rows: List<AttendanceFull>, config: Config, period: Period): AttendanceUiState {
        if (rows.isEmpty()) return AttendanceUiState.Empty
        return AttendanceUiState.Content(
            listOf(
                AttendanceSummaryAggregator.aggregate(rows, period, config.currentSemester),
                AttendanceTab.DaysTab(buildDays(rows, config)),
                AttendanceTab.MonthsTab(buildMonths(rows, config)),
                AttendanceTab.TypesTab(buildTypes(rows, config)),
                AttendanceTab.ListTab(buildList(rows)),
            ),
        )
    }

    private fun leafOf(row: AttendanceFull) = AttendanceLeaf(row, unseen = !row.seen)

    private fun anchorOf(date: Date): Long = date.year * 10000L + date.month * 100L + date.day

    private class ScratchRange(var rangeStart: Date, var rangeEnd: Date, val items: MutableList<AttendanceFull>)

    private fun buildDays(rows: List<AttendanceFull>, config: Config): List<DayRangeHeader> {
        // The undated rows leave before groupBy and before the consecutive-day merge below: filed at
        // 2019-01-01 they would otherwise fold into a real run dated 2019-01-02, the defect itself.
        // This partition runs on the PRESENT-filtered rows, so an undated set of only PRESENT rows is
        // empty here and adds no header; buildMonths, which does not filter, makes its own check.
        val (undated, dated) = rows
            .filter { it.baseType != Attendance.TYPE_PRESENT }
            .partition { it.dateUnknown }
        val ranges = dated
            .groupBy { it.date }
            .map { (date, dayRows) -> ScratchRange(date, date, dayRows.toMutableList()) }
            .toMutableList()

        val grouped = if (config.groupConsecutiveDays && ranges.size > 1) {
            ranges.sortByDescending { it.rangeStart }   // newest first; rangeEnd already == rangeStart
            val merged = mutableListOf<ScratchRange>()
            var current = ranges.first()
            for (i in 1 until ranges.size) {
                val next = ranges[i]
                if (Date.diffDays(current.rangeStart, next.rangeStart) <= 1) {
                    current.items.addAll(next.items)        // fold the older day into the run
                    current.rangeStart = next.rangeStart    // start moves older; end (newest) stays
                } else {
                    merged.add(current)
                    current = next
                }
            }
            merged.add(current)
            merged
        } else {
            ranges
        }

        val realDays = grouped.map { range ->
            val leaves = range.items.map(::leafOf)
            DayRangeHeader(
                key = NodeKey.DayRangeKey(anchorOf(range.rangeEnd)),
                rangeStart = range.rangeStart,
                rangeEnd = range.rangeEnd,
                hasUnseen = leaves.any { it.unseen },
                expanded = false,
                leaves = leaves,
            )
        }
        // Appended last, and only when it has rows: groupBy emits a header only for a non-empty set, and
        // this is the one header that would not, so an unconditional append would put an empty
        // "date unknown" row on every install that has never had an undated attendance.
        return if (undated.isEmpty()) realDays
        else realDays + unknownDayHeader(undated.map(::leafOf))
    }

    private fun unknownDayHeader(leaves: List<AttendanceLeaf>) = DayRangeHeader(
        key = NodeKey.UnknownDayKey,
        rangeStart = filingDate(),
        rangeEnd = filingDate(),
        hasUnseen = leaves.any { it.unseen },
        expanded = false,
        leaves = leaves,
    )

    /**
     * The date the Librus parser files an undated attendance under (`librusAttendanceDate`). The unknown
     * headers carry it so their date fields hold a defined value; nothing reads them, because every
     * render site branches on `dateUnknown` first. A fresh instance per call: Date is mutable.
     */
    private fun filingDate() = Date(2019, 1, 1)

    private fun buildMonths(rows: List<AttendanceFull>, config: Config): List<MonthHeader> {
        // Unlike buildDays this does not drop PRESENT first, so the undated partition is empty only
        // when there is no undated row at all, and an all-PRESENT undated set still gets its bucket.
        val (undated, dated) = rows.partition { it.dateUnknown }
        val realMonths = dated
            .groupBy { it.date.year to it.date.month }
            .map { (ym, monthRows) ->
                monthHeader(NodeKey.MonthKey(ym.first, ym.second), ym.first, ym.second, monthRows, config)
            }
        // Appended last, and only when it has rows - see buildDays.
        return if (undated.isEmpty()) realMonths
        else realMonths + unknownMonthHeader(undated, config)
    }

    /**
     * Keeps the counts, which say "this many absences could not be dated", and drops the percentage.
     * present-counted / counted-total is an attendance rate; over a parse failure it is a rate over no
     * period, sitting in a column of monthly rates that invites comparison. `AttendanceBarChart` still
     * labels each segment, and that stays: those labels are each type's share of this bucket's own
     * rows, a true statement about what the bucket contains, not a rate.
     */
    private fun unknownMonthHeader(rows: List<AttendanceFull>, config: Config): MonthHeader {
        val filing = filingDate()
        return monthHeader(NodeKey.UnknownMonthKey, filing.year, filing.month, rows, config)
            .copy(percentage = null)
    }

    private fun monthHeader(
        key: NodeKey,
        year: Int,
        month: Int,
        monthRows: List<AttendanceFull>,
        config: Config,
    ): MonthHeader {
        val counts = AttendanceCounts.snapshot(monthRows)   // counts BEFORE dropping PRESENT
        val kept = if (config.showPresenceInMonth) monthRows
        else monthRows.filter { it.baseType != Attendance.TYPE_PRESENT }
        val leaves = kept.map(::leafOf)
        return MonthHeader(
            key = key,
            year = year,
            month = month,
            counts = counts,
            percentage = AttendanceCounts.percentage(counts),
            hasUnseen = leaves.any { it.unseen },
            expanded = false,
            leaves = leaves,
        )
    }

    private fun buildTypes(rows: List<AttendanceFull>, config: Config): List<TypeHeader> {
        val total = rows.size
        return rows
            // Grouped by the same three fields NodeKey.TypeKey keys on, NOT by the whole
            // AttendanceType. It is a data class, so grouping by it compares all seven fields
            // while the key compares three - two types differing only in typeShort or typeColor
            // then became two rows sharing one key, and LazyColumn threw. A school defining two
            // flavours of one type is a single row to the user, so the pill takes the first
            // variant in encounter order; the DAO's ORDER BY makes that stable.
            .groupBy { it.typeObject.let { type -> Triple(type.id, type.typeName, type.typeSymbol) } }
            .map { (_, typeRows) ->
                val type = typeRows.first().typeObject
                val leaves = typeRows.map(::leafOf)
                TypeHeader(
                    key = NodeKey.TypeKey(type),
                    type = type,
                    sharePercent = if (total == 0) null else typeRows.size.toFloat() / total.toFloat(),
                    semesterCount = typeRows.count { it.semester == config.currentSemester },
                    yearCount = leaves.size,
                    hasUnseen = leaves.any { it.unseen },
                    expanded = false,
                    leaves = leaves,
                )
            }
            .sortedBy { it.yearCount }   // legacy: sortedBy { items.size }
    }

    private fun buildList(rows: List<AttendanceFull>): List<AttendanceLeaf> =
        rows.filter { it.baseType != Attendance.TYPE_PRESENT }.map(::leafOf)
}
