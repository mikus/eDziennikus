/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import eu.mikus.edziennik.data.db.full.GradeFull
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GradesStatsAggregatorTest {

    private val roundedGrade: (Float) -> Int = { v -> v.toInt() + if (v % 1f >= 0.75f) 1 else 0 }

    private fun avg(normalAvg: Float? = null, pointAvgPercent: Float? = null) =
        AveragesSnapshot(0f, 0, 0f, 0f, 0f, 0f, 0f, normalAvg, pointAvgPercent)

    private fun finalGrade(value: Float): GradeFull = mockk(relaxed = true) { every { this@mockk.value } returns value }

    private fun sem(number: Int, normalAvg: Float? = null, finalValue: Float? = null, proposedValue: Float? = null, pointPct: Float? = null) =
        SemesterItem(
            subjectId = 1, number = number, grades = emptyList(),
            proposedGrade = proposedValue?.let { finalGrade(it) }, finalGrade = finalValue?.let { finalGrade(it) },
            averages = avg(normalAvg, pointPct), hasUnseen = false, hideEditor = false, expanded = false,
        )

    private fun subject(semesters: List<SemesterItem>, normalAvg: Float? = null, finalValue: Float? = null, isUnknown: Boolean = false) =
        SubjectItem(
            subjectId = 1, name = "S", isUnknown = isUnknown, semesters = semesters,
            proposedGrade = null, finalGrade = finalValue?.let { finalGrade(it) },
            averages = avg(normalAvg), gradeCount = 0, hasUnseen = false, expanded = false,
            firstNonEmptySemesterNumber = semesters.firstOrNull()?.number,
        )

    @Test fun `expected fallback uses rounded semester average when no final grade`() {
        val stats = GradesStatsAggregator.aggregate(
            listOf(subject(listOf(sem(1, normalAvg = 4.8f)))),
            roundedGrade,
        )
        assertEquals(5f, stats.normalSem1)
        assertEquals(0f, stats.normalSem1Final)
        assertTrue(stats.sem1NotAllFinal)
    }

    @Test fun `final grade feeds both final and expected`() {
        val stats = GradesStatsAggregator.aggregate(
            listOf(subject(listOf(sem(1, normalAvg = 3f, finalValue = 5f)))),
            roundedGrade,
        )
        assertEquals(5f, stats.normalSem1)
        assertEquals(5f, stats.normalSem1Final)
        assertFalse(stats.sem1NotAllFinal)
    }

    @Test fun `yearly averages average across subjects and point averages roll up`() {
        val a = subject(listOf(sem(1, normalAvg = 4f, pointPct = 80f)), normalAvg = 4f)
        val b = subject(listOf(sem(1, normalAvg = 6f, pointPct = 60f)), normalAvg = 6f)
        val stats = GradesStatsAggregator.aggregate(listOf(a, b), roundedGrade)
        assertEquals(5f, stats.normalYearly)
        assertEquals(70f, stats.pointSem1)
    }

    @Test fun `proposed grade without final feeds the proposed average and expected falls back to rounded normalAvg`() {
        val stats = GradesStatsAggregator.aggregate(
            listOf(subject(listOf(sem(1, normalAvg = 3f, proposedValue = 5f)))),
            roundedGrade,
        )
        assertEquals(5f, stats.normalSem1Proposed)
        assertEquals(3f, stats.normalSem1)        // no final → expected = roundedGrade(3.0) = 3
        assertEquals(0f, stats.normalSem1Final)
    }

    @Test fun `sem2 routes to sem2 fields, independent of sem1`() {
        val s = subject(listOf(sem(1, normalAvg = 4f, finalValue = 4f), sem(2, normalAvg = 6f, finalValue = 6f)))
        val stats = GradesStatsAggregator.aggregate(listOf(s), roundedGrade)
        assertEquals(4f, stats.normalSem1Final)
        assertEquals(6f, stats.normalSem2Final)   // sem2 not mixed into sem1
    }

    @Test fun `unknown subjects are excluded`() {
        val stats = GradesStatsAggregator.aggregate(
            listOf(subject(listOf(sem(1, normalAvg = 4f)), isUnknown = true)),
            roundedGrade,
        )
        assertEquals(0f, stats.normalSem1)
    }
}
