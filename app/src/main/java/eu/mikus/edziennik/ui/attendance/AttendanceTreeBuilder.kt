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
        val ranges = rows
            .filter { it.baseType != Attendance.TYPE_PRESENT }
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

        return grouped.map { range ->
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
    }

    private fun buildMonths(rows: List<AttendanceFull>, config: Config): List<MonthHeader> =
        rows
            .groupBy { it.date.year to it.date.month }
            .map { (ym, monthRows) ->
                val counts = AttendanceCounts.snapshot(monthRows)   // counts BEFORE dropping PRESENT
                val kept = if (config.showPresenceInMonth) monthRows
                else monthRows.filter { it.baseType != Attendance.TYPE_PRESENT }
                val leaves = kept.map(::leafOf)
                MonthHeader(
                    key = NodeKey.MonthKey(ym.first, ym.second),
                    year = ym.first,
                    month = ym.second,
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
            .groupBy { it.typeObject }
            .map { (type, typeRows) ->
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
