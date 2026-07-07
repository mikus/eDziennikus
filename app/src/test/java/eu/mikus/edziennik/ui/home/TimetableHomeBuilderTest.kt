/*
 * Copyright (c) Mikolaj Olszewski 2026-7-6.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TimetableHomeBuilderTest {

    private val today = Date(2026, 6, 1)   // Monday

    private fun lesson(
        date: Date = today,
        start: Time = Time(8, 0, 0),
        end: Time = Time(8, 45, 0),
        type: Int = Lesson.TYPE_NORMAL,
        cancelled: Boolean = false,
        subject: String? = "Subj",
    ): LessonFull = mockk(relaxed = true) {
        every { displayDate } returns date
        every { displayStartTime } returns start
        every { displayEndTime } returns end
        every { this@mockk.type } returns type
        every { isCancelled } returns cancelled
        every { isChange } returns false
        every { displaySubjectName } returns subject
    }

    @Test
    fun `empty window yields NoTimetable with week start`() {
        val s = assertIs<TimetableHomeUiState.NoTimetable>(
            TimetableHomeBuilder.build(emptyList(), now = Time(7, 0, 0), today = today),
        )
        assertEquals("2026-06-01", s.weekStart)
    }

    @Test
    fun `only NO_LESSONS across the week yields NoLessons`() {
        val markers = (0..7).map { lesson(date = Date(2026, 6, 1 + it), type = Lesson.TYPE_NO_LESSONS) }
        val state = TimetableHomeBuilder.build(markers, now = Time(7, 0, 0), today = today)
        assertIs<TimetableHomeUiState.NoLessons>(state)
    }

    @Test
    fun `today with remaining lessons yields Content TODAY, counter window on the first`() {
        val l1 = lesson(start = Time(8, 0, 0), end = Time(8, 45, 0))
        val l2 = lesson(start = Time(9, 0, 0), end = Time(9, 45, 0))
        val c = TimetableHomeBuilder.build(listOf(l1, l2), now = Time(7, 30, 0), today = today) as TimetableHomeUiState.Content
        assertEquals(TimetableHomeUiState.Mode.TODAY, c.mode)
        assertEquals(2, c.lessonCount)
        assertEquals(l1, c.firstLesson)
        assertEquals(Time(8, 0, 0).stringHM, c.counterStart?.stringHM)
        assertTrue(c.showAllLessons)              // not yet ongoing at 7:30
    }

    @Test
    fun `ongoing first lesson hides itself from nextLessons`() {
        val l1 = lesson(start = Time(8, 0, 0), end = Time(8, 45, 0))
        val l2 = lesson(start = Time(9, 0, 0), end = Time(9, 45, 0))
        val c = TimetableHomeBuilder.build(listOf(l1, l2), now = Time(8, 10, 0), today = today) as TimetableHomeUiState.Content
        assertTrue(!c.showAllLessons)             // ongoing
        assertEquals(listOf(l2), c.nextLessons.map { it.lesson })   // current dropped
    }

    @Test
    fun `today all past then steps to tomorrow (Content TOMORROW)`() {
        val past = lesson(date = today, start = Time(7, 0, 0), end = Time(7, 45, 0))
        val tmr = lesson(date = Date(2026, 6, 2), start = Time(8, 0, 0), end = Time(8, 45, 0))
        val c = TimetableHomeBuilder.build(listOf(past, tmr), now = Time(9, 0, 0), today = today) as TimetableHomeUiState.Content
        assertEquals(TimetableHomeUiState.Mode.TOMORROW, c.mode)
        assertEquals(tmr, c.firstLesson)
        assertEquals(null, c.counterStart)        // future mode: static
        assertTrue(c.showAllLessons)
    }

    @Test
    fun `free days bridge to a later this-week day (THIS_WEEK mode)`() {
        val monMarker = lesson(date = today, type = Lesson.TYPE_NO_LESSONS)                 // Mon free
        val tueMarker = lesson(date = Date(2026, 6, 2), type = Lesson.TYPE_NO_LESSONS)       // Tue free
        val wed = lesson(date = Date(2026, 6, 3), start = Time(8, 0, 0), end = Time(8, 45, 0)) // Wed real
        val c = TimetableHomeBuilder.build(listOf(monMarker, tueMarker, wed), now = Time(7, 0, 0), today = today) as TimetableHomeUiState.Content
        assertEquals(TimetableHomeUiState.Mode.THIS_WEEK, c.mode)
        assertEquals(wed, c.firstLesson)
        assertEquals(2, c.dayInfoArgs.size)   // [dayName, dateStr]
    }

    @Test
    fun `all-cancelled today is skipped`() {
        val c1 = lesson(start = Time(8, 0, 0), end = Time(9, 0, 0), cancelled = true)
        val tmr = lesson(date = Date(2026, 6, 2), start = Time(8, 0, 0), end = Time(8, 45, 0))
        val c = TimetableHomeBuilder.build(listOf(c1, tmr), now = Time(7, 0, 0), today = today) as TimetableHomeUiState.Content
        assertEquals(tmr, c.firstLesson)
    }
}
