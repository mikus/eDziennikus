/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades.editor

/** One editable/simulated grade row. */
data class EditorGrade(
    val id: Long,
    val name: String,
    val value: Float,
    val category: String,
    val weight: Float,
)

/** A pickable grade name -> its numeric value (the 19-entry menu catalog). */
data class EditorGradeOption(val name: String, val value: Float)

/** Lowercased+trimmed "don't count" grade names + whether the feature is on. */
data class DontCountConfig(val enabled: Boolean, val names: Set<String>)

/** Weighted sum/count + average for one semester's editable grades. */
data class SemesterStats(val sum: Float, val count: Float, val average: Float)

/** The other-semester inputs (from GradesEditorArgs) needed for the year average. */
data class OtherSemester(val sum: Float, val count: Float, val average: Float, val final: Float)

sealed interface GradesEditorUiState {
    data object Loading : GradesEditorUiState
    data object SubjectMissing : GradesEditorUiState
    data class Content(
        val subjectName: String,
        val semester: Int,
        val averageBefore: Float,
        val averageAfter: Float,
        val yearAverageVisible: Boolean,
        val yearAverageBefore: Float,
        val yearAverageAfter: Float,
        val grades: List<EditorGrade>,
    ) : GradesEditorUiState
}
