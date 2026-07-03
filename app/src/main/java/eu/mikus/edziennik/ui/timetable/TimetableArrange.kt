/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

/**
 * Pure port of Tachyon's cluster/column overlap layout (Google-Calendar style).
 *
 * 1. Sort by (startMinute, endMinute).
 * 2. Group into clusters of transitively-overlapping spans; a new cluster starts when a span's
 *    start is >= the running max end of the current cluster (so `end == start` = NOT overlapping).
 * 3. Within a cluster, place each span in the first column whose last span ends `<= this.start`,
 *    else open a new column. Every span in the cluster gets the cluster's final column count
 *    (uniform widths within a cluster).
 *
 * Returns [Positioned] in the SAME order as the input (via [Positioned.index]).
 */
object TimetableArrange {

    data class LessonSpan(val startMinute: Int, val endMinute: Int)
    data class Positioned(val index: Int, val column: Int, val columnCount: Int)

    fun arrange(items: List<LessonSpan>): List<Positioned> {
        if (items.isEmpty()) return emptyList()

        val sorted = items.withIndex().sortedWith(
            compareBy({ it.value.startMinute }, { it.value.endMinute }),
        )

        val out = ArrayList<Positioned>(items.size)
        val columnsEnd = ArrayList<Int>()            // last end minute placed in each column
        val pending = ArrayList<Pair<Int, Int>>()    // (originalIndex, column) for the current cluster
        var clusterMaxEnd = Int.MIN_VALUE

        fun flushCluster() {
            val count = columnsEnd.size
            for ((origIndex, col) in pending) out += Positioned(origIndex, col, count)
            pending.clear()
            columnsEnd.clear()
            clusterMaxEnd = Int.MIN_VALUE
        }

        for ((origIndex, span) in sorted) {
            if (pending.isNotEmpty() && span.startMinute >= clusterMaxEnd) flushCluster()

            var placed = columnsEnd.indexOfFirst { it <= span.startMinute }
            if (placed == -1) {
                columnsEnd.add(span.endMinute)
                placed = columnsEnd.size - 1
            } else {
                columnsEnd[placed] = span.endMinute
            }
            pending += origIndex to placed
            clusterMaxEnd = maxOf(clusterMaxEnd, span.endMinute)
        }
        if (pending.isNotEmpty()) flushCluster()

        return out.sortedBy { it.index }
    }
}
