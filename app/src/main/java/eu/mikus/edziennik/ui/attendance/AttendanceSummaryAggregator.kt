/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.full.AttendanceFull

/**
 * Pure aggregator for the SUMMARY tab. Owns the period filter (so the list tabs cannot honor it).
 * The overall % is computed over the whole filtered set, which equals the legacy
 * sum-of-per-subject (present/total) because that membership is per-type and additive across subjects.
 */
object AttendanceSummaryAggregator {

    fun aggregate(
        rows: List<AttendanceFull>,
        period: Period,
        currentSemester: Int,
    ): AttendanceTab.SummaryTab {
        val filtered = when (period) {
            Period.ALL -> rows
            Period.SEM1 -> rows.filter { it.semester == 1 }
            Period.SEM2 -> rows.filter { it.semester == 2 }
        }

        val subjects = filtered
            .groupBy { it.subjectId }
            .map { (subjectId, subjectRows) ->
                val counts = AttendanceCounts.snapshot(subjectRows)      // counts BEFORE stripping PRESENT
                val leaves = subjectRows
                    .filter { it.baseType != Attendance.TYPE_PRESENT }
                    .map { AttendanceLeaf(it, unseen = !it.seen) }
                SubjectHeader(
                    key = NodeKey.SubjectKey(subjectId),
                    subjectId = subjectId,
                    name = subjectRows.firstOrNull()?.subjectLongName ?: "",
                    counts = counts,
                    percentage = AttendanceCounts.percentage(counts),
                    hasUnseen = leaves.any { it.unseen },
                    expanded = false,
                    leaves = leaves,
                )
            }
            .sortedBy { it.name.lowercase() }

        val overallCounts = AttendanceCounts.snapshot(filtered)
        val stats = SummaryStats(
            overallPercent = AttendanceCounts.percentage(overallCounts),
            counts = overallCounts,
        )
        return AttendanceTab.SummaryTab(stats = stats, subjects = subjects)
    }
}
