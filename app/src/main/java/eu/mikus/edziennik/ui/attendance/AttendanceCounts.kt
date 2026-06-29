/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.full.AttendanceFull

/**
 * Pure attendance counting. Seam-free — reads type.isCounted / type.baseType directly off the entity.
 * Home of the present-counted / counted-total formula (used by Summary + Months only, NOT Types).
 */
object AttendanceCounts {

    fun snapshot(rows: List<AttendanceFull>): CountSnapshot =
        CountSnapshot(
            rows.groupBy { it.typeObject }
                .map { TypeCount(it.key, it.value.size) }
                .sortedBy { it.type }, // canonical AttendanceType.compareTo order
        )

    /** present-counted / counted-total as a 0f..1f fraction; null when counted-total == 0. */
    fun percentage(counts: CountSnapshot): Float? {
        val totalCount = counts.byType.sumOf {
            if (!it.type.isCounted || it.type.baseType == Attendance.TYPE_UNKNOWN) 0 else it.count
        }
        if (totalCount == 0) return null
        val presenceCount = counts.byType.sumOf {
            when (it.type.baseType) {
                Attendance.TYPE_PRESENT,
                Attendance.TYPE_PRESENT_CUSTOM,
                Attendance.TYPE_BELATED,
                Attendance.TYPE_BELATED_EXCUSED,
                Attendance.TYPE_RELEASED -> if (it.type.isCounted) it.count else 0
                else -> 0
            }
        }
        return presenceCount.toFloat() / totalCount.toFloat()
    }
}
