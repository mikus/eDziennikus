/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.TeacherAbsenceFull
import eu.mikus.edziennik.utils.models.Date

/**
 * Pure, Android-free. Builds the calendar dot-map + the selected day's items, replicating the legacy
 * agenda screen's rules: lesson-changes group by displayDate (null dropped); teacher-absences expand
 * across [dateFrom, dateTo]; never mutate the source entities (stepForward mutates, so iterate a fresh cursor).
 * The selected day's events preserve input order, so `events` is expected pre-sorted by date/time
 * (as `EventDao.getAll` returns them: ORDER BY eventDate, eventTime, addedDate).
 */
object AgendaBuilder {

    data class Config(
        val agendaLessonChanges: Boolean,
        val agendaTeacherAbsence: Boolean,
    )

    private class DotAcc {
        val colors = mutableListOf<Int>()
        var hasUnseen = false
    }

    fun build(
        events: List<EventFull>,
        lessonChanges: List<LessonFull>,
        teacherAbsences: List<TeacherAbsenceFull>,
        config: Config,
        selectedDate: Date,
    ): AgendaUiState {
        val acc = LinkedHashMap<Date, DotAcc>()
        fun dot(date: Date) = acc.getOrPut(date) { DotAcc() }

        for (e in events) {
            val d = dot(e.date)
            d.colors += e.eventColor
            if (!e.seen) d.hasUnseen = true
        }
        if (config.agendaLessonChanges) {
            for (lc in lessonChanges) {
                val date = lc.displayDate ?: continue   // shift-aware; null dropped
                dot(date)                               // marker only (no colour)
            }
        }
        if (config.agendaTeacherAbsence) {
            for (ta in teacherAbsences) {
                // fresh owned cursor — stepForward MUTATES; never step the entity's own Date
                val cursor = Date.fromValue(ta.dateFrom.value)
                while (cursor <= ta.dateTo) {
                    dot(Date.fromValue(cursor.value))   // a fresh, stable key per day
                    cursor.stepForward(0, 0, 1)
                }
            }
        }

        if (acc.isEmpty()) return AgendaUiState.Empty

        val monthDots = acc.mapValues { (_, a) -> DayDots(a.colors.toList(), a.hasUnseen) }

        val dayItems = mutableListOf<AgendaItem>()
        if (config.agendaLessonChanges) {
            val n = lessonChanges.count { it.displayDate == selectedDate }
            if (n > 0) dayItems += AgendaItem.LessonChangesItem(selectedDate, n)
        }
        if (config.agendaTeacherAbsence) {
            val n = teacherAbsences.count { spans(it, selectedDate) }
            if (n > 0) dayItems += AgendaItem.TeacherAbsenceItem(selectedDate, n)
        }
        dayItems += events.filter { it.date == selectedDate }
            .map { AgendaItem.EventItem(it, unseen = !it.seen) }

        return AgendaUiState.Content(monthDots, selectedDate, dayItems)
    }

    private fun spans(ta: TeacherAbsenceFull, date: Date): Boolean =
        ta.dateFrom <= date && date <= ta.dateTo
}
