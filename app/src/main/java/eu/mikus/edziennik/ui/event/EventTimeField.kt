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
 * [Ticket] (from [open], [ticket] or [chooseDate]) when it starts building, and hands the rows back
 * to [install] when the build is done. Anything the user does in between happens before [install]
 * reads the selection.
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

    /** Issued when a build of the rows starts; see [install]. */
    class Ticket internal constructor(internal val generation: Int, internal val kind: Kind)

    internal sealed interface Kind {
        data object Opening : Kind
        data object Sync : Kind
        data class Chosen(val lesson: EventTimeField.Opening.At?) : Kind
    }

    var items: List<FormDropdownItem> = emptyList()
        private set
    var selectedId: Long? = null
        private set

    /** Bumped by every date choice; a ticket from an earlier generation was issued for another date. */
    private var generation = 0

    /** The ticket for the dialog's first build of the rows, for its opening date. */
    fun open(): Ticket = Ticket(generation, Kind.Opening)

    /** The ticket for a rebuild of the current date's rows, e.g. after a sync. */
    fun ticket(): Ticket = Ticket(generation, Kind.Sync)

    /**
     * The user chose a date: the time is cleared until that date's rows arrive. With a [lesson] (the
     * "next lesson" choice) its start is selected then.
     */
    fun chooseDate(lesson: LessonFull?): Ticket {
        generation++
        selectedId = null
        val at = lesson?.let { l -> l.displayStartTime?.let { Opening.At(it, l.id, l.displaySubjectId) } }
        return Ticket(generation, Kind.Chosen(at))
    }

    /**
     * The rows built for [ticket] are ready. Returns `false`, changing nothing, when they no longer
     * belong in the field.
     */
    fun install(ticket: Ticket, rows: List<FormDropdownItem>): Boolean {
        when (val kind = ticket.kind) {
            Kind.Opening -> {
                items = rows
                if (selectedId == null) apply(opening)
            }
            Kind.Sync -> {
                if (ticket.generation != generation) return false
                // The sync may have re-keyed the selected lesson (see EventTimeItems), so take the row
                // selected NOW (possibly picked during the build) from the list it was picked in, and
                // find it again in the rebuilt one.
                val held = items.firstOrNull { it.id == selectedId }
                val reselected = EventTimeItems.reselect(rows, held)
                items = reselected?.items ?: rows
                selectedId = reselected?.selectedId
                (opening as? Opening.At)?.let { apply(it) }
            }
            is Kind.Chosen -> {
                items = rows
                kind.lesson?.let { apply(it) }
            }
        }
        return true
    }

    /** The user tapped the row [id]; null for a row that clears the field ("no timetable"/"no lessons"). */
    fun pick(id: Long?) {
        selectedId = id
    }

    /** The user picked [time] in the custom-time picker. */
    fun pickCustom(time: Time) = select(time)

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
