/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.ui.grades.models.GradesAverages

/** Immutable projection of one subject's/semester's [GradesAverages] (nothing mutable escapes into Content). */
data class AveragesSnapshot(
    val normalSum: Float,
    val normalCount: Int,
    val normalWeightedSum: Float,
    val normalWeightedCount: Float,
    val pointSum: Float,
    val pointAvgSum: Float,
    val pointAvgMax: Float,
    val normalAvg: Float?,
    val pointAvgPercent: Float?,
)

fun GradesAverages.snapshot() = AveragesSnapshot(
    normalSum = normalSum,
    normalCount = normalCount,
    normalWeightedSum = normalWeightedSum,
    normalWeightedCount = normalWeightedCount,
    pointSum = pointSum,
    pointAvgSum = pointAvgSum,
    pointAvgMax = pointAvgMax,
    normalAvg = normalAvg,
    pointAvgPercent = pointAvgPercent,
)

/** Inverse of [snapshot] — kept next to it so the matched 9-field mapping is reviewed together.
 *  Used by the host to feed GradesManager's GradesAverages-typed display methods. Round-trip tested. */
fun AveragesSnapshot.toGradesAverages() = GradesAverages().also {
    it.normalSum = normalSum
    it.normalCount = normalCount
    it.normalWeightedSum = normalWeightedSum
    it.normalWeightedCount = normalWeightedCount
    it.pointSum = pointSum
    it.pointAvgSum = pointAvgSum
    it.pointAvgMax = pointAvgMax
    it.normalAvg = normalAvg
    it.pointAvgPercent = pointAvgPercent
}

data class SemesterItem(
    val subjectId: Long,
    val number: Int,
    val grades: List<GradeFull>,
    val proposedGrade: GradeFull?,
    val finalGrade: GradeFull?,
    val averages: AveragesSnapshot,
    val hasUnseen: Boolean,
    val hideEditor: Boolean,
    val expanded: Boolean,
)

data class SubjectItem(
    val subjectId: Long,
    val name: String,
    val isUnknown: Boolean,
    val semesters: List<SemesterItem>,
    val proposedGrade: GradeFull?,
    val finalGrade: GradeFull?,
    val averages: AveragesSnapshot,
    val gradeCount: Int,
    val hasUnseen: Boolean,
    val expanded: Boolean,
    val firstNonEmptySemesterNumber: Int?,
)

/** The nine school-year averages footer (mirrors GradesStats minus the three university* fields). */
data class StatsItem(
    val normalSem1: Float,
    val normalSem1Proposed: Float,
    val normalSem1Final: Float,
    val sem1NotAllFinal: Boolean,
    val normalSem2: Float,
    val normalSem2Proposed: Float,
    val normalSem2Final: Float,
    val sem2NotAllFinal: Boolean,
    val normalYearly: Float,
    val normalYearlyProposed: Float,
    val normalYearlyFinal: Float,
    val yearlyNotAllFinal: Boolean,
    val pointSem1: Float,
    val pointSem2: Float,
    val pointYearly: Float,
)

/** The legacy GradesEditor "what-if" payload, produced by GradesViewModel.editorArgs. */
data class GradesEditorArgs(
    val subjectId: Long,
    val semester: Int,
    val averageMode: Int,
    val yearAverageBefore: Float?,
    val gradeSumOtherSemester: Float?,
    val gradeCountOtherSemester: Float?,
    val averageOtherSemester: Float?,
    val finalOtherSemester: Float?,
)

sealed interface GradesUiState {
    data object Loading : GradesUiState
    data object Empty : GradesUiState
    data object Unsupported : GradesUiState
    data class Content(val subjects: List<SubjectItem>, val stats: StatsItem) : GradesUiState
}
