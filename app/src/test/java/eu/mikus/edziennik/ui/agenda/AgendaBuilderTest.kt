/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.TeacherAbsenceFull
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AgendaBuilderTest {

    private val cfg = AgendaBuilder.Config(agendaLessonChanges = true, agendaTeacherAbsence = true)

    private fun event(id: Long, date: Date, color: Int = 0xFF2196F3.toInt(), seen: Boolean = true): EventFull =
        mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.date } returns date
            every { eventColor } returns color
            every { this@mockk.seen } returns seen
        }

    private fun change(displayDate: Date?, seen: Boolean = true): LessonFull =
        mockk(relaxed = true) {
            every { this@mockk.displayDate } returns displayDate
            every { this@mockk.seen } returns seen
        }

    private fun absence(from: Date, to: Date): TeacherAbsenceFull =
        mockk(relaxed = true) {
            every { dateFrom } returns from
            every { dateTo } returns to
        }

    @Test
    fun `empty everything yields Empty`() {
        assertEquals(
            AgendaUiState.Empty,
            AgendaBuilder.build(emptyList(), emptyList(), emptyList(), cfg, Date(2026, 6, 1)),
        )
    }

    @Test
    fun `events produce coloured dots and selected-day EventItems`() {
        val state = AgendaBuilder.build(
            events = listOf(
                event(1, Date(2026, 6, 1), color = 0xFFAA0000.toInt(), seen = false),
                event(2, Date(2026, 6, 1)),
                event(3, Date(2026, 6, 5)),
            ),
            lessonChanges = emptyList(), teacherAbsences = emptyList(),
            config = cfg, selectedDate = Date(2026, 6, 1),
        ) as AgendaUiState.Content

        assertEquals(2, state.monthDots[Date(2026, 6, 1)]!!.colors.size)
        assertTrue(state.monthDots[Date(2026, 6, 1)]!!.hasUnseen)
        assertTrue(state.monthDots.containsKey(Date(2026, 6, 5)))
        assertEquals(2, state.dayItems.filterIsInstance<AgendaItem.EventItem>().size)
        assertTrue(state.dayItems.filterIsInstance<AgendaItem.EventItem>().any { it.unseen })
    }

    @Test
    fun `teacher-absence is expanded across its date range without mutating the entity`() {
        val ta = absence(Date(2026, 6, 1), Date(2026, 6, 3))
        val state = AgendaBuilder.build(
            emptyList(), emptyList(), listOf(ta), cfg, selectedDate = Date(2026, 6, 2),
        ) as AgendaUiState.Content

        assertTrue(state.monthDots.containsKey(Date(2026, 6, 1)))
        assertTrue(state.monthDots.containsKey(Date(2026, 6, 2)))
        assertTrue(state.monthDots.containsKey(Date(2026, 6, 3)))
        assertEquals(1, state.dayItems.filterIsInstance<AgendaItem.TeacherAbsenceItem>().single().count)
        assertEquals(Date(2026, 6, 1).value, ta.dateFrom.value)   // entity not mutated
    }

    @Test
    fun `lesson-changes group by displayDate and drop null-date rows`() {
        val state = AgendaBuilder.build(
            events = emptyList(),
            lessonChanges = listOf(change(Date(2026, 6, 4)), change(Date(2026, 6, 4)), change(null)),
            teacherAbsences = emptyList(),
            config = cfg, selectedDate = Date(2026, 6, 4),
        ) as AgendaUiState.Content

        assertTrue(state.monthDots.containsKey(Date(2026, 6, 4)))
        assertEquals(1, state.monthDots.size)
        assertEquals(2, state.dayItems.filterIsInstance<AgendaItem.LessonChangesItem>().single().count)
    }

    @Test
    fun `config gates disable change and absence contributions`() {
        val off = AgendaBuilder.Config(agendaLessonChanges = false, agendaTeacherAbsence = false)
        val state = AgendaBuilder.build(
            events = emptyList(),
            lessonChanges = listOf(change(Date(2026, 6, 4))),
            teacherAbsences = listOf(absence(Date(2026, 6, 4), Date(2026, 6, 4))),
            config = off, selectedDate = Date(2026, 6, 4),
        )
        assertEquals(AgendaUiState.Empty, state)
    }

    @Test
    fun `summary rows come before events in dayItems`() {
        val state = AgendaBuilder.build(
            events = listOf(event(1, Date(2026, 6, 4))),
            lessonChanges = listOf(change(Date(2026, 6, 4))),
            teacherAbsences = listOf(absence(Date(2026, 6, 4), Date(2026, 6, 4))),
            config = cfg, selectedDate = Date(2026, 6, 4),
        ) as AgendaUiState.Content
        assertTrue(state.dayItems[0] is AgendaItem.LessonChangesItem)
        assertTrue(state.dayItems[1] is AgendaItem.TeacherAbsenceItem)
        assertTrue(state.dayItems[2] is AgendaItem.EventItem)
    }

    @Test
    fun `month has data but selected day empty yields Content with empty dayItems`() {
        val state = AgendaBuilder.build(
            events = listOf(event(1, Date(2026, 6, 5))),
            lessonChanges = emptyList(), teacherAbsences = emptyList(),
            config = cfg, selectedDate = Date(2026, 6, 1),
        )
        assertTrue(state is AgendaUiState.Content)
        assertTrue((state as AgendaUiState.Content).dayItems.isEmpty())
    }
}
