/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class TimetableCoverageTest {

    private fun rowOn(date: Date): LessonFull = mockk(relaxed = true) {
        every { displayDate } returns date
    }

    @Test
    fun `a gap ahead reports that day's week`() {
        // The reported case: today Sunday, Mon-Wed absent from the response, Thu/Fri present.
        val today = Date(2026, 9, 13)
        val rows = listOf(rowOn(Date(2026, 9, 17)), rowOn(Date(2026, 9, 18)))
        assertEquals("2026-09-14", missingWeekStart(rows, today))
    }

    @Test
    fun `a fully covered window reports nothing`() {
        val today = Date(2026, 6, 1)
        val rows = (0..7).map { rowOn(today.clone().stepForward(0, 0, it)) }
        assertNull(missingWeekStart(rows, today))
    }

    @Test
    fun `today's own gap is never offered`() {
        // Rows on every day ahead but none for today. Offering today's week would be a Sync button
        // that fetches a week already behind us - which is how this defect looks to a user.
        val today = Date(2026, 6, 1)
        val rows = (1..7).map { rowOn(today.clone().stepForward(0, 0, it)) }
        assertNull(missingWeekStart(rows, today))
    }

    @Test
    fun `an empty window reports the next day's week`() {
        // Sunday: the useful week to fetch is the one starting tomorrow, not the one ending today.
        assertEquals("2026-06-08", missingWeekStart(emptyList(), Date(2026, 6, 7)))
    }

    @Test
    fun `the gap's week is reported, not the gap itself`() {
        // Every other case here has a Monday gap, where `.weekStart` is a no-op - so without this
        // test, deleting `.weekStart` entirely would pass the whole class. A mid-week gap is what
        // distinguishes "report the week" from "report the day", and it also rejects `lastOrNull`.
        val monday = Date(2026, 6, 1)
        val rows = listOf(rowOn(Date(2026, 6, 2)))   // Tue only, so the first gap is Wed 06-03
        assertEquals("2026-06-01", missingWeekStart(rows, monday))
    }
}
