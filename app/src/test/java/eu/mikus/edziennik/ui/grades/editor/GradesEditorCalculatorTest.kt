/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades.editor

import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_AVG_2_AVG
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_AVG_2_SEM
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_SEM_2_AVG
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_ALL_GRADES
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradesEditorCalculatorTest {

    private fun grade(
        id: Long = 1, name: String = "4", value: Float = 4f, weight: Float = 1f,
        type: Int = Grade.TYPE_NORMAL, semester: Int = 1, description: String? = "desc", category: String? = "cat",
    ): Grade = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.name } returns name
        every { this@mockk.value } returns value
        every { this@mockk.weight } returns weight
        every { this@mockk.type } returns type
        every { this@mockk.semester } returns semester
        every { this@mockk.description } returns description
        every { this@mockk.category } returns category
    }

    private val noDontCount = DontCountConfig(enabled = false, names = emptySet())

    @Test
    fun `toEditorGrades keeps only TYPE_NORMAL, weight ge 0, matching semester`() {
        val grades = listOf(
            grade(id = 1, semester = 1, weight = 1f),
            grade(id = 2, semester = 2, weight = 1f),                       // wrong semester
            grade(id = 3, semester = 1, weight = -1f),                      // historical (weight < 0)
            grade(id = 4, semester = 1, weight = 1f, type = 100),          // not TYPE_NORMAL
        )
        val out = GradesEditorCalculator.toEditorGrades(grades, semester = 1)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun `toEditorGrades maps category to description dash category`() {
        val out = GradesEditorCalculator.toEditorGrades(listOf(grade(description = "Sprawdzian", category = "Dział 1")), 1)
        assertEquals("Sprawdzian - Dział 1", out.single().category)
    }

    @Test
    fun `semesterStats weights the values`() {
        val grades = listOf(
            EditorGrade(1, "5", 5f, "", 2f),
            EditorGrade(2, "3", 3f, "", 1f),
        )
        val s = GradesEditorCalculator.semesterStats(grades, noDontCount)
        assertEquals(13f, s.sum)     // 5*2 + 3*1
        assertEquals(3f, s.count)    // 2 + 1
        assertEquals(13f / 3f, s.average)
    }

    @Test
    fun `semesterStats zeroes weight for dont-count names`() {
        val grades = listOf(EditorGrade(1, "5", 5f, "", 2f), EditorGrade(2, "nb", 0f, "", 1f))
        val s = GradesEditorCalculator.semesterStats(grades, DontCountConfig(true, setOf("nb")))
        assertEquals(10f, s.sum)
        assertEquals(2f, s.count)
    }

    @Test
    fun `semesterStats empty is zero not NaN`() {
        val s = GradesEditorCalculator.semesterStats(emptyList(), noDontCount)
        assertEquals(0f, s.average)
    }

    @Test
    fun `yearAverage all grades combines both semesters' sums and counts`() {
        val sem = SemesterStats(sum = 10f, count = 2f, average = 5f)
        val other = OtherSemester(sum = 6f, count = 2f, average = 3f, final = 4f)
        assertEquals(16f / 4f, GradesEditorCalculator.yearAverage(YEAR_ALL_GRADES, sem, other))
    }

    @Test
    fun `yearAverage avg-avg averages the two semester averages`() {
        val sem = SemesterStats(0f, 0f, 5f); val other = OtherSemester(0f, 0f, 3f, 4f)
        assertEquals(4f, GradesEditorCalculator.yearAverage(YEAR_1_AVG_2_AVG, sem, other))
    }

    @Test
    fun `yearAverage sem-avg and avg-sem use the other-semester final`() {
        val sem = SemesterStats(0f, 0f, 5f); val other = OtherSemester(0f, 0f, 3f, 4f)
        assertEquals(4.5f, GradesEditorCalculator.yearAverage(YEAR_1_SEM_2_AVG, sem, other))
        assertEquals(4.5f, GradesEditorCalculator.yearAverage(YEAR_1_AVG_2_SEM, sem, other))
    }

    @Test
    fun `colorGradeInt floors and bumps at three-quarters`() {
        assertEquals(4, GradesEditorCalculator.colorGradeInt(4.74f))
        assertEquals(5, GradesEditorCalculator.colorGradeInt(4.75f))
        assertEquals(3, GradesEditorCalculator.colorGradeInt(3.0f))
    }

    @Test
    fun `GRADE_OPTIONS has 19 entries and maps names to value id over 100`() {
        assertEquals(19, GradesEditorCalculator.GRADE_OPTIONS.size)
        assertEquals(0.75f, GradesEditorCalculator.GRADE_OPTIONS.first { it.name == "1-" }.value)
        assertEquals(6.5f, GradesEditorCalculator.GRADE_OPTIONS.first { it.name == "6+" }.value)
        assertTrue(GradesEditorCalculator.GRADE_OPTIONS.any { it.name == "0" && it.value == 0f })
    }
}
