/*
 * Copyright (c) Mikolaj Olszewski 2026-11-5.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.knownSubjectId
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.isTimetableBlock
import eu.mikus.edziennik.data.db.full.knownSubjectId

/**
 * The events that belong on [lesson]: those at its start time, minus the ones that belong to
 * another block sharing that time slot.
 *
 * The slot is every block in [dayLessons] on the same day with the same display start time
 * (compared by value), where "block" is [isTimetableBlock]. The rule owns this definition, so
 * [dayLessons] may be any superset of this profile's day (one profile's lessons, any number of
 * days; the widget passes a whole week) and callers cannot pre-filter it into disagreement. The
 * rule never checks the profile, so lessons of another profile would add owners. The slot's
 * owners are the known subjects of its blocks.
 *
 * An event at the lesson's time is kept iff its subject is unknown, or no block in the slot
 * carries it (an orphan), or it is the lesson's own subject. Input order is preserved.
 *
 * Orphans show on every block of the slot: the data cannot say which block they were meant for,
 * and a reminder hidden costs more than one shown twice. Likewise a block of unknown subject
 * takes only orphans and unknown-subject events, never an event another block owns.
 *
 * `Event.teamId` is deliberately not read. Synced events (Librus events with a class, Synergia
 * homework, parent-teacher meetings) and lesson-less manual events carry the whole-class team
 * id, and lessons carry
 * it too whenever Librus sends a class, so narrowing by team would read "for the whole class"
 * as "not for this group" on a slot holding a whole-class block and a group block.
 *
 * Two blocks of the same subject both show its event; there is no cancelled-versus-ran tiebreak.
 */
fun List<EventFull>.forLesson(lesson: LessonFull, dayLessons: List<LessonFull>): List<EventFull> {
    val start = lesson.displayStartTime?.value ?: return emptyList()
    val owners = dayLessons
        .filter { it.isTimetableBlock && it.displayDate == lesson.displayDate && it.displayStartTime?.value == start }
        .mapNotNull { it.knownSubjectId }
        .toSet()
    return filter { event ->
        val subject = event.knownSubjectId
        event.time?.value == start && (subject == null || subject !in owners || subject == lesson.knownSubjectId)
    }
}
