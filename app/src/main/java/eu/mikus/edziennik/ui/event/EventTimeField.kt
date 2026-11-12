/*
 * Copyright (c) Mikolaj Olszewski 2026-11-12.
 */
package eu.mikus.edziennik.ui.event

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ui.dialogs.base.FormDropdownItem
import eu.mikus.edziennik.utils.models.Time

/**
 * The time field of [EventManualDialog]: its rows, which one is selected, and every decision about
 * what to select when the rows are rebuilt. The dialog is the controller — it turns taps, pickers
 * and EventBus events into calls here and shows [items] and [selectedId] — so the decisions can be
 * tested on the JVM, which the dialog cannot.
 *
 * The rows are rebuilt off the main thread, so a rebuild is split in two: the caller takes a
 * [Ticket] (from [ticket] or [chooseDate]) when it starts building, and hands the rows back
 * to [install] when the build is done. Anything the user does in between happens before [install]
 * reads the selection. A date chosen in between makes the ticket stale: rows built for the old date,
 * and everything else the caller still had in flight for it, no longer belong (see [isCurrent]).
 */
class EventTimeField(private val opening: Opening) {

    /** The time the dialog opened with. */
    sealed interface Opening {
        data object None : Opening
        data object AllDay : Opening
        /** [preferLessonId] / [preferSubjectId] pick between lessons sharing [time]; see [EventTimeItems.select]. */
        data class At(
            val time: Time,
            val preferLessonId: Long? = null,
            val preferSubjectId: Long? = null,
        ) : Opening

        companion object {
            /**
             * An edited event opens on its own time (with its subject, which is what tells apart two
             * lessons at that time), or all day when it has none; otherwise the lesson the dialog was
             * opened from, if it has a start; otherwise nothing.
             */
            fun of(editingEvent: EventFull?, defaultLesson: LessonFull?): Opening =
                editingEvent?.let { event ->
                    event.time?.let { At(it, preferSubjectId = event.subjectId) } ?: AllDay
                } ?: defaultLesson?.let { lesson ->
                    lesson.displayStartTime?.let { At(it, lesson.id, lesson.displaySubjectId) }
                } ?: None
        }
    }

    /** Issued when a build of the rows (or anything else that belongs to the current date) starts. */
    class Ticket internal constructor(internal val generation: Int)

    /** What the user last asked the field for: what a rebuild of the rows must keep. */
    private sealed interface Intent {
        /** Nothing touched yet: every rebuild re-applies the [opening] time. */
        data object Opening : Intent
        /** A "next lesson" date choice whose rows no install has shown yet. */
        data class Lesson(val at: EventTimeField.Opening.At) : Intent
        /** The user chose: keep what is selected, found again in the rebuilt rows. */
        data object Held : Intent
    }

    var items: List<FormDropdownItem> = emptyList()
        private set
    var selectedId: Long? = null
        private set

    /** Bumped by every date choice; a ticket from an earlier generation was issued for another date. */
    private var generation = 0

    private var intent: Intent = Intent.Opening

    /**
     * The ticket for a build of the current date's rows (the dialog's first one, or one after a sync),
     * or for a next-lesson lookup.
     */
    fun ticket(): Ticket = Ticket(generation)

    /** Whether no date was chosen since [ticket] was issued. */
    fun isCurrent(ticket: Ticket): Boolean = ticket.generation == generation

    /**
     * The user chose a date: the time and the rows are cleared until that date's rows arrive (a row of
     * the old date picked meanwhile would be for the wrong day). With a [lesson] (the "next lesson"
     * choice) its start is selected then, by whichever install shows those rows first.
     */
    fun chooseDate(lesson: LessonFull?): Ticket {
        generation++
        items = emptyList()
        selectedId = null
        intent = lesson?.let { l ->
            l.displayStartTime?.let { Intent.Lesson(Opening.At(it, l.id, l.displaySubjectId)) }
        } ?: Intent.Held
        return Ticket(generation)
    }

    /**
     * The rows built for [ticket] are ready. Returns `false`, changing nothing, when they no longer
     * belong in the field.
     *
     * Otherwise they replace the list and the selection follows the user's most recent intent: the
     * opening time until they touch the time or the date (re-applied with its lesson and subject, which
     * is what tells apart two lessons at that time), then whatever they chose.
     */
    fun install(ticket: Ticket, rows: List<FormDropdownItem>): Boolean {
        if (!isCurrent(ticket)) return false
        // Read the selection now, not when the ticket was issued: a pick made during the build is the
        // most recent choice. It is a row of the list it was picked in, and a sync may have re-keyed
        // it (see EventTimeItems), so it is found again in the new rows.
        val held = items.firstOrNull { it.id == selectedId }
        items = rows
        selectedId = null
        when (val i = intent) {
            Intent.Opening -> apply(opening)
            is Intent.Lesson -> {
                apply(i.at)
                intent = Intent.Held
            }
            Intent.Held -> EventTimeItems.reselect(rows, held)?.let {
                items = it.items
                selectedId = it.selectedId
            }
        }
        return true
    }

    /** The user tapped the row [id]; null for a row that clears the field ("no timetable"/"no lessons"). */
    fun pick(id: Long?) {
        selectedId = id
        intent = Intent.Held
    }

    /** The user picked [time] in the custom-time picker. */
    fun pickCustom(time: Time) {
        select(time)
        intent = Intent.Held
    }

    /** What the selected row stands for. */
    fun resolved(): TimeSelection {
        val id = selectedId ?: return TimeSelection.None
        if (id == EventTimeItems.ALL_DAY_ID) return TimeSelection.AllDay
        val item = items.firstOrNull { it.id == id } ?: return TimeSelection.None
        return when (val tag = item.tag) {
            is LessonFull -> EventManualLogic.lessonToTimeSelection(tag)
            is TimeSelection -> tag
            else -> TimeSelection.None
        }
    }

    private fun apply(opening: Opening) {
        when (opening) {
            Opening.None -> Unit
            Opening.AllDay -> selectedId = EventTimeItems.ALL_DAY_ID
            is Opening.At -> select(opening.time, opening.preferLessonId, opening.preferSubjectId)
        }
    }

    private fun select(time: Time, preferLessonId: Long? = null, preferSubjectId: Long? = null) {
        val selection = EventTimeItems.select(items, time, preferLessonId, preferSubjectId)
        items = selection.items
        selectedId = selection.selectedId
    }
}
