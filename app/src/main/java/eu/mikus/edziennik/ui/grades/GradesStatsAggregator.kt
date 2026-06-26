/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

object GradesStatsAggregator {

    /** Ports GradesListFragment stats loop (361-429): builds the nine averages + notAllFinal flags. */
    fun aggregate(subjects: List<SubjectItem>, roundedGrade: (Float) -> Int): StatsItem {
        val sem1Expected = mutableListOf<Float>(); val sem1Proposed = mutableListOf<Float>(); val sem1Final = mutableListOf<Float>()
        val sem2Expected = mutableListOf<Float>(); val sem2Proposed = mutableListOf<Float>(); val sem2Final = mutableListOf<Float>()
        val yearExpected = mutableListOf<Float>(); val yearProposed = mutableListOf<Float>(); val yearFinal = mutableListOf<Float>()
        val sem1Point = mutableListOf<Float>(); val sem2Point = mutableListOf<Float>(); val yearPoint = mutableListOf<Float>()

        for (subject in subjects) {
            if (subject.isUnknown) continue
            subject.semesters.forEach { sem ->
                val (proposed, final, expected, point) = when (sem.number) {
                    1 -> Quad(sem1Proposed, sem1Final, sem1Expected, sem1Point)
                    2 -> Quad(sem2Proposed, sem2Final, sem2Expected, sem2Point)
                    else -> return@forEach
                }
                sem.proposedGrade?.value?.let { proposed += it }
                sem.finalGrade?.value?.let {
                    final += it; expected += it
                } ?: sem.averages.normalAvg?.let { expected += roundedGrade(it).toFloat() }
                sem.averages.pointAvgPercent?.let { point += it }
            }
            subject.proposedGrade?.value?.let { yearProposed += it }
            subject.finalGrade?.value?.let {
                yearFinal += it; yearExpected += it
            } ?: subject.averages.normalAvg?.let { yearExpected += roundedGrade(it).toFloat() }
            subject.averages.pointAvgPercent?.let { yearPoint += it }
        }

        // Double-precision intermediate (then narrow once) — bit-faithful to the legacy `average()?.toFloat() ?: 0f`.
        fun List<Float>.avgOrZero() = if (isEmpty()) 0f else (sumOf { it.toDouble() } / size).toFloat()

        return StatsItem(
            normalSem1 = sem1Expected.avgOrZero(),
            normalSem1Proposed = sem1Proposed.avgOrZero(),
            normalSem1Final = sem1Final.avgOrZero(),
            sem1NotAllFinal = sem1Final.size < sem1Expected.size,
            normalSem2 = sem2Expected.avgOrZero(),
            normalSem2Proposed = sem2Proposed.avgOrZero(),
            normalSem2Final = sem2Final.avgOrZero(),
            sem2NotAllFinal = sem2Final.size < sem2Expected.size,
            normalYearly = yearExpected.avgOrZero(),
            normalYearlyProposed = yearProposed.avgOrZero(),
            normalYearlyFinal = yearFinal.avgOrZero(),
            yearlyNotAllFinal = yearFinal.size < yearExpected.size,
            pointSem1 = sem1Point.avgOrZero(),
            pointSem2 = sem2Point.avgOrZero(),
            pointYearly = yearPoint.avgOrZero(),
        )
    }

    private data class Quad(
        val proposed: MutableList<Float>,
        val final: MutableList<Float>,
        val expected: MutableList<Float>,
        val point: MutableList<Float>,
    )
}
