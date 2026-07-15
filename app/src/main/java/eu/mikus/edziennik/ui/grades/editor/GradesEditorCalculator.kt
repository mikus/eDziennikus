/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades.editor

import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_AVG_2_AVG
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_AVG_2_SEM
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_SEM_2_AVG
import kotlin.math.floor

/** Pure what-if averages math for the Grades editor (ported from GradesEditorFragment). */
object GradesEditorCalculator {

    /** raw Grade entities -> editable rows: keep TYPE_NORMAL, drop weight<0 (historical), keep `semester`. */
    fun toEditorGrades(grades: List<Grade>, semester: Int): List<EditorGrade> =
        grades
            .filter { it.type == Grade.TYPE_NORMAL && it.weight >= 0f && it.semester == semester }
            .map { EditorGrade(it.id, it.name, it.value, "${it.description} - ${it.category}", it.weight) }

    fun semesterStats(grades: List<EditorGrade>, dontCount: DontCountConfig): SemesterStats {
        var sum = 0f
        var count = 0f
        for (g in grades) {
            val weight = if (dontCount.enabled && dontCount.names.contains(g.name.lowercase().trim())) 0f else g.weight
            sum += g.value * weight
            count += weight
        }
        return SemesterStats(sum, count, if (count == 0f) 0f else sum / count)
    }

    fun yearAverage(mode: Int, sem: SemesterStats, other: OtherSemester): Float = when (mode) {
        YEAR_1_AVG_2_AVG -> (other.average + sem.average) / 2f
        YEAR_1_SEM_2_AVG, YEAR_1_AVG_2_SEM -> (other.final + sem.average) / 2f
        else -> {                                             // YEAR_ALL_GRADES + fallback
            val denom = other.count + sem.count
            if (denom == 0f) 0f else (other.sum + sem.sum) / denom
        }
    }

    /** floor(avg), bumped by 1 when the fractional part is >= .75 — the legacy color-bucket rule. */
    fun colorGradeInt(average: Float): Int {
        var gradeInt = floor(average.toDouble()).toInt()
        if (average % 1 >= 0.75f) gradeInt++
        return gradeInt
    }

    /** The 19-entry grade-name -> value catalog (value = legacy menuId / 100). */
    val GRADE_OPTIONS: List<EditorGradeOption> = listOf(
        "1-" to 0.75f, "1" to 1.0f, "1+" to 1.5f, "2-" to 1.75f, "2" to 2.0f, "2+" to 2.5f,
        "3-" to 2.75f, "3" to 3.0f, "3+" to 3.5f, "4-" to 3.75f, "4" to 4.0f, "4+" to 4.5f,
        "5-" to 4.75f, "5" to 5.0f, "5+" to 5.5f, "6-" to 5.75f, "6" to 6.0f, "6+" to 6.5f, "0" to 0.0f,
    ).map { EditorGradeOption(it.first, it.second) }
}
