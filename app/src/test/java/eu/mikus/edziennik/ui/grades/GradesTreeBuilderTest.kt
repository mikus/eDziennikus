/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.ui.grades.GradesTreeBuilder.Config
import eu.mikus.edziennik.ui.grades.GradesTreeBuilder.Math
import eu.mikus.edziennik.ui.grades.models.GradesAverages
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GradesTreeBuilderTest {

    // Stub math: value = grade.value, weight = grade.weight, semester avg = weightedSum/weightedCount,
    // year avg = mean of semester normalAvgs, roundedGrade = floor + (>=.75 ? 1 : 0).
    private val math = Math(
        gradeValue = { it.value },
        gradeWeight = { it.weight },
        semesterAverage = { a: GradesAverages ->
            if (a.pointAvgMax != 0f) a.pointAvgPercent = a.pointAvgSum / a.pointAvgMax * 100f
            if (a.normalWeightedCount > 0f) a.normalAvg = a.normalWeightedSum / a.normalWeightedCount
        },
        yearAverage = { a: GradesAverages, sems ->
            val avgs = sems.mapNotNull { it.normalAvg }
            if (avgs.isNotEmpty()) a.normalAvg = avgs.average().toFloat()
        },
        roundedGrade = { v -> v.toInt() + if (v % 1f >= 0.75f) 1 else 0 },
    )

    private fun cfg(
        hideNoGrade: Boolean = false,
        hideSticks: Boolean = false,
        hideImproved: Boolean = false,
        orderBy: Int = 0,
        university: Boolean = false,
    ) = Config(university, hideNoGrade, hideSticks, hideImproved, orderBy)

    private fun grade(
        id: Long,
        subjectId: Long,
        subjectLongName: String? = "Subject $subjectId",
        semester: Int = 1,
        type: Int = Grade.TYPE_NORMAL,
        value: Float = 4f,
        weight: Float = 1f,
        name: String = "4",
        seen: Boolean = true,
        isImprovement: Boolean = false,
        addedDate: Long = id,
    ): GradeFull = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.subjectId } returns subjectId
        every { this@mockk.subjectLongName } returns subjectLongName
        every { this@mockk.semester } returns semester
        every { this@mockk.type } returns type
        every { this@mockk.type = any() } returns Unit
        every { this@mockk.value } returns value
        every { this@mockk.weight } returns weight
        every { this@mockk.name } returns name
        every { this@mockk.seen } returns seen
        every { this@mockk.showAsUnseen = any() } returns Unit
        every { this@mockk.isImprovement } returns isImprovement
        every { isImproved } returns isImprovement   // computed getter is mocked under relaxed=true, so stub it directly (not via parentId)
        every { this@mockk.addedDate } returns addedDate
    }

    @Test fun `university config short-circuits to Unsupported`() {
        val s = GradesTreeBuilder.build(listOf(grade(1, 10)), cfg(university = true), math)
        assertEquals(GradesUiState.Unsupported, s)
    }

    @Test fun `no grades yields Empty`() {
        assertEquals(GradesUiState.Empty, GradesTreeBuilder.build(emptyList(), cfg(), math))
    }

    @Test fun `groups by subject then semester and routes grade types`() {
        val grades = listOf(
            grade(1, 10, semester = 1, type = Grade.TYPE_NORMAL, value = 5f),
            grade(2, 10, semester = 1, type = Grade.TYPE_SEMESTER1_PROPOSED, name = "5"),
            grade(3, 10, semester = 1, type = Grade.TYPE_SEMESTER1_FINAL, name = "5"),
            grade(4, 10, semester = 1, type = Grade.TYPE_YEAR_FINAL, name = "5"),
        )
        val content = GradesTreeBuilder.build(grades, cfg(), math) as GradesUiState.Content
        assertEquals(1, content.subjects.size)
        val subject = content.subjects[0]
        assertEquals(10L, subject.subjectId)
        assertEquals(4L, subject.finalGrade?.id)
        val sem = subject.semesters.single { it.number == 1 }
        assertEquals(listOf(1L), sem.grades.map { it.id })
        assertEquals(2L, sem.proposedGrade?.id)
        assertEquals(3L, sem.finalGrade?.id)
    }

    @Test fun `countGrade accumulates normal sum and weighted average into snapshot`() {
        val grades = listOf(
            grade(1, 10, value = 4f, weight = 1f),
            grade(2, 10, value = 6f, weight = 3f),
        )
        val content = GradesTreeBuilder.build(grades, cfg(), math) as GradesUiState.Content
        val sem = content.subjects[0].semesters[0]
        assertEquals(10f, sem.averages.normalSum)
        assertEquals(2, sem.averages.normalCount)
        assertEquals(22f, sem.averages.normalWeightedSum)
        assertEquals(4f, sem.averages.normalWeightedCount)
        assertEquals(5.5f, sem.averages.normalAvg)
    }

    @Test fun `hideNoGrade drops TYPE_NO_GRADE`() {
        val grades = listOf(
            grade(1, 10, type = Grade.TYPE_NORMAL),
            grade(2, 10, type = Grade.TYPE_NO_GRADE),
        )
        val content = GradesTreeBuilder.build(grades, cfg(hideNoGrade = true), math) as GradesUiState.Content
        assertEquals(listOf(1L), content.subjects[0].semesters[0].grades.map { it.id })
    }

    @Test fun `hideImproved drops seen improved grades from the semester`() {
        val grades = listOf(
            grade(1, 10, seen = true, isImprovement = true),
            grade(2, 10, seen = false, isImprovement = true),
            grade(3, 10, isImprovement = false),
        )
        val content = GradesTreeBuilder.build(grades, cfg(hideImproved = true), math) as GradesUiState.Content
        assertEquals(listOf(2L, 3L), content.subjects[0].semesters[0].grades.map { it.id }.sorted())
    }

    @Test fun `gradeCount counts unfiltered grades even with hideImproved`() {
        val grades = listOf(
            grade(1, 10, seen = true, isImprovement = true),
            grade(2, 10, isImprovement = false),
        )
        val content = GradesTreeBuilder.build(grades, cfg(hideImproved = true), math) as GradesUiState.Content
        assertEquals(1, content.subjects[0].semesters[0].grades.size)
        assertEquals(2, content.subjects[0].gradeCount)
    }

    @Test fun `unknown subject becomes one isUnknown bucket and normalizes type to TYPE_NORMAL`() {
        val captured = mutableListOf<Int>()
        val g = grade(1, 99, subjectLongName = null, type = Grade.TYPE_YEAR_FINAL)
        every { g.type = capture(captured) } returns Unit
        val content = GradesTreeBuilder.build(listOf(g), cfg(), math) as GradesUiState.Content
        val subject = content.subjects.single()
        assertTrue(subject.isUnknown)
        assertTrue(Grade.TYPE_NORMAL in captured)
    }

    @Test fun `surfaces firstNonEmptySemesterNumber and captured unseen`() {
        val grades = listOf(
            grade(1, 10, semester = 2, value = 4f),
            grade(2, 10, semester = 1, type = Grade.TYPE_SEMESTER1_PROPOSED, seen = false),
        )
        val content = GradesTreeBuilder.build(grades, cfg(), math) as GradesUiState.Content
        assertEquals(2, content.subjects[0].firstNonEmptySemesterNumber)
        assertTrue(content.subjects[0].hasUnseen)
    }

    @Test fun `orderBy date desc vs asc sorts subjects by lastAddedDate`() {
        val grades = listOf(
            grade(1, 10, addedDate = 100L),
            grade(2, 20, addedDate = 200L),
        )
        val desc = GradesTreeBuilder.build(grades, cfg(orderBy = GradesManagerOrder.DATE_DESC), math) as GradesUiState.Content
        assertEquals(listOf(20L, 10L), desc.subjects.map { it.subjectId })
        val asc = GradesTreeBuilder.build(grades, cfg(orderBy = GradesManagerOrder.DATE_ASC), math) as GradesUiState.Content
        assertEquals(listOf(10L, 20L), asc.subjects.map { it.subjectId })
    }

    @Test fun `preserves DAO semester order (current first) and picks firstNonEmpty from unfiltered scratch`() {
        // DAO orders gradeSemester DESC, so the sem2 grade arrives first → sem2 displayed first (legacy)
        val grades = listOf(
            grade(1, 10, semester = 2, value = 5f),
            grade(2, 10, semester = 1, value = 4f),
        )
        val subject = (GradesTreeBuilder.build(grades, cfg(), math) as GradesUiState.Content).subjects[0]
        assertEquals(listOf(2, 1), subject.semesters.map { it.number })   // current semester displayed first
        assertEquals(2, subject.firstNonEmptySemesterNumber)              // and auto-expanded
    }

    @Test fun `point grades accumulate into pointAvg and pointSum`() {
        val pAvg = grade(1, 10, type = Grade.TYPE_POINT_AVG, value = 8f).also { every { it.valueMax } returns 10f }
        val pSum = grade(2, 10, type = Grade.TYPE_POINT_SUM, value = 3f)
        val sem = (GradesTreeBuilder.build(listOf(pAvg, pSum), cfg(), math) as GradesUiState.Content).subjects[0].semesters[0]
        assertEquals(8f, sem.averages.pointAvgSum)
        assertEquals(10f, sem.averages.pointAvgMax)
        assertEquals(3f, sem.averages.pointSum)
        assertEquals(80f, sem.averages.pointAvgPercent)   // semesterAverage seam: 8/10*100
    }

    @Test fun `year average seam receives both semesters settled inputs`() {
        val grades = listOf(
            grade(1, 10, semester = 1, value = 4f, weight = 1f),
            grade(2, 10, semester = 2, value = 6f, weight = 1f),
        )
        val subject = (GradesTreeBuilder.build(grades, cfg(), math) as GradesUiState.Content).subjects[0]
        assertEquals(5f, subject.averages.normalAvg)   // semesterAvgs 4 & 6 → year mean 5 (via yearAverage stub)
    }

    @Test fun `AveragesSnapshot round-trips through toGradesAverages on all nine fields`() {
        val src = GradesAverages().also {
            it.normalSum = 12f; it.normalCount = 3; it.normalWeightedSum = 20f; it.normalWeightedCount = 5f
            it.pointSum = 7f; it.pointAvgSum = 8f; it.pointAvgMax = 10f; it.normalAvg = 4f; it.pointAvgPercent = 80f
        }
        val back = src.snapshot().toGradesAverages()
        assertEquals(src.normalSum, back.normalSum)
        assertEquals(src.normalCount, back.normalCount)
        assertEquals(src.normalWeightedSum, back.normalWeightedSum)
        assertEquals(src.normalWeightedCount, back.normalWeightedCount)
        assertEquals(src.pointSum, back.pointSum)
        assertEquals(src.pointAvgSum, back.pointAvgSum)
        assertEquals(src.pointAvgMax, back.pointAvgMax)
        assertEquals(src.normalAvg, back.normalAvg)
        assertEquals(src.pointAvgPercent, back.pointAvgPercent)
    }
}

/** Mirror of GradesManager order constants used by the builder's post-build sort (kept local to avoid an App dep in tests). */
object GradesManagerOrder {
    const val DATE_DESC = 0
    const val DATE_ASC = 2
}
