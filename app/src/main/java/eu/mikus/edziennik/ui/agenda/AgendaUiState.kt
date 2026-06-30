/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.utils.models.Date

/** Per-day calendar indicator. colors = that day's event colours (may be empty for a change/absence-only day). */
data class DayDots(val colors: List<Int>, val hasUnseen: Boolean)

/** An item in the selected day's list. lazyKey is a Bundle-safe stable LazyColumn key (the Phase-6 lesson). */
sealed interface AgendaItem {
    val lazyKey: String

    data class EventItem(val event: EventFull, val unseen: Boolean) : AgendaItem {
        override val lazyKey get() = "e${event.id}"
    }
    data class LessonChangesItem(val date: Date, val count: Int) : AgendaItem {
        override val lazyKey get() = "lc-${date.value}"
    }
    data class TeacherAbsenceItem(val date: Date, val count: Int) : AgendaItem {
        override val lazyKey get() = "ta-${date.value}"
    }
}

sealed interface AgendaUiState {
    data object Loading : AgendaUiState
    data object Empty : AgendaUiState
    /** monthDots: a day is a key iff it has any events / (enabled) change / (enabled) absence. */
    data class Content(
        val monthDots: Map<Date, DayDots>,
        val selectedDate: Date,
        val dayItems: List<AgendaItem>,
    ) : AgendaUiState
}
