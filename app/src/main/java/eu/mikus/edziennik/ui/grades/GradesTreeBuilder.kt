/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.ui.grades.models.GradesAverages
import eu.mikus.edziennik.ui.grades.models.GradesSemester
import eu.mikus.edziennik.ui.grades.models.GradesSubject
import kotlin.math.max

object GradesTreeBuilder {

    const val ORDER_BY_DATE_DESC = 0
    const val ORDER_BY_DATE_ASC = 2

    data class Config(
        val isUniversity: Boolean,
        val hideNoGrade: Boolean,
        val hideSticksFromOldDevMode: Boolean,
        val hideImproved: Boolean,
        val orderBy: Int,
    )

    /** Settled per-semester inputs the year-average seam needs (keeps the legacy GradesSemester out of the contract). */
    data class SemesterAvgInput(val number: Int, val normalAvg: Float?, val finalValue: Float?)

    class Math(
        val gradeValue: (Grade) -> Float,
        val gradeWeight: (Grade) -> Float,
        val semesterAverage: (GradesAverages) -> Unit,
        val yearAverage: (GradesAverages, List<SemesterAvgInput>) -> Unit,
        val roundedGrade: (Float) -> Int,
    )

    fun build(grades: List<GradeFull>, config: Config, math: Math): GradesUiState {
        if (config.isUniversity) return GradesUiState.Unsupported

        val source = if (config.hideSticksFromOldDevMode) grades.filter { it.value != 1.0f } else grades

        // --- grouping (ports processGrades:216-302, K-12 path) ---
        val scratch = mutableListOf<GradesSubject>()
        var unknown: GradesSubject? = null
        var subjectId = -1L
        var semesterNumber = 0
        var subject = GradesSubject(subjectId, "")
        var semester = GradesSemester(0, 1)

        for (grade in source) {
            if (config.hideNoGrade && grade.type == Grade.TYPE_NO_GRADE) continue

            if (grade.subjectId != subjectId) {
                subjectId = grade.subjectId
                semesterNumber = 0
                subject = scratch.firstOrNull { it.subjectId == subjectId } ?: run {
                    grade.subjectLongName?.let { name ->
                        GradesSubject(grade.subjectId, name).also { it.semester = 2; scratch += it }
                    } ?: (unknown ?: GradesSubject(-1, "unknown").also {
                        it.semester = 2; it.isUnknown = true; scratch += it; unknown = it
                    })
                }
            }
            if (grade.semester != semesterNumber) {
                semesterNumber = grade.semester
                semester = subject.semesters.firstOrNull { it.number == semesterNumber }
                    ?: GradesSemester(subject.subjectId, grade.semester).also {
                        it.hideEditor = subject.isUnknown; subject.semesters += it
                    }
            }

            grade.showAsUnseen = !grade.seen
            if (!grade.seen) {
                if (grade.type == Grade.TYPE_YEAR_PROPOSED || grade.type == Grade.TYPE_YEAR_FINAL) subject.hasUnseen = true
                else semester.hasUnseen = true
            }

            if (subject.isUnknown) grade.type = Grade.TYPE_NORMAL   // unknown subjects: normalize (I10)

            when (grade.type) {
                Grade.TYPE_SEMESTER1_PROPOSED, Grade.TYPE_SEMESTER2_PROPOSED -> semester.proposedGrade = grade
                Grade.TYPE_SEMESTER1_FINAL, Grade.TYPE_SEMESTER2_FINAL -> semester.finalGrade = grade
                Grade.TYPE_YEAR_PROPOSED -> subject.proposedGrade = grade
                Grade.TYPE_YEAR_FINAL -> subject.finalGrade = grade
                else -> {
                    semester.grades += grade
                    countGrade(grade, subject.averages, math)
                    countGrade(grade, semester.averages, math)
                }
            }
            subject.lastAddedDate = max(subject.lastAddedDate, grade.addedDate)
        }

        if (scratch.isEmpty()) return GradesUiState.Empty

        // --- averages (semester first, then year) ---
        for (subj in scratch) {
            if (subj.isUnknown) continue
            subj.semesters.forEach { math.semesterAverage(it.averages) }
            math.yearAverage(
                subj.averages,
                subj.semesters.map { SemesterAvgInput(it.number, it.averages.normalAvg, it.finalGrade?.value) },
            )
        }

        // --- order ---
        when (config.orderBy) {
            ORDER_BY_DATE_DESC -> scratch.sortByDescending { it.lastAddedDate }
            ORDER_BY_DATE_ASC -> scratch.sortBy { it.lastAddedDate }
        }

        // --- project to immutable items ---
        val subjects = scratch.map { subj ->
            // Preserve scratch insertion order (DAO orders gradeSemester DESC → current semester first),
            // matching legacy display + auto-expand target (GradesAdapter:162); do NOT re-sort by number.
            val semesterItems = subj.semesters.map { sem ->
                val grades = if (config.hideImproved) sem.grades.filter { !it.seen || !it.isImproved } else sem.grades
                SemesterItem(
                    subjectId = subj.subjectId,
                    number = sem.number,
                    grades = grades,
                    proposedGrade = sem.proposedGrade,
                    finalGrade = sem.finalGrade,
                    averages = sem.averages.snapshot(),
                    hasUnseen = sem.hasUnseen,
                    hideEditor = sem.hideEditor,
                    expanded = false,
                )
            }
            SubjectItem(
                subjectId = subj.subjectId,
                name = subj.subjectName,
                isUnknown = subj.isUnknown,
                semesters = semesterItems,
                proposedGrade = subj.proposedGrade,
                finalGrade = subj.finalGrade,
                averages = subj.averages.snapshot(),
                gradeCount = subj.semesters.sumOf { it.grades.size },   // UNFILTERED scratch count (legacy SubjectViewHolder:77)
                hasUnseen = subj.hasUnseen,
                expanded = false,
                // UNFILTERED scratch, insertion order — matches legacy GradesAdapter:162 (firstOrNull{grades nonEmpty} ?: first)
                firstNonEmptySemesterNumber =
                    (subj.semesters.firstOrNull { it.grades.isNotEmpty() } ?: subj.semesters.firstOrNull())?.number,
            )
        }

        return GradesUiState.Content(subjects, GradesStatsAggregator.aggregate(subjects, math.roundedGrade))
    }

    /** Ports GradesListFragment.countGrade:434-457. Accumulation set is intentionally CLOSED to
     *  {TYPE_NORMAL, TYPE_POINT_AVG, TYPE_POINT_SUM} — any other type contributes nothing to averages
     *  (the routing `when` above already diverts proposed/final/no-grade); keep this set + the routing in sync. */
    private fun countGrade(grade: Grade, averages: GradesAverages, math: Math) {
        val value = math.gradeValue(grade)
        val weight = math.gradeWeight(grade)
        when (grade.type) {
            Grade.TYPE_NORMAL -> {
                if (grade.value > 0f) {
                    averages.normalSum += value
                    averages.normalCount++
                }
                averages.normalWeightedSum += value * weight
                averages.normalWeightedCount += weight
            }
            Grade.TYPE_POINT_AVG -> {
                averages.pointAvgSum += grade.value
                averages.pointAvgMax += grade.valueMax ?: value
            }
            Grade.TYPE_POINT_SUM -> averages.pointSum += grade.value
        }
    }
}
