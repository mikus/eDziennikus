/*
 * Copyright (c) Mikolaj Olszewski 2026-11-5.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.knownSubjectId
import eu.mikus.edziennik.data.db.entity.knownTeamId
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
 * Two stages decide it: subject, then team.
 *
 * **Subject.** An event at the lesson's time is a *candidate* for a block iff its subject is
 * unknown, or no block in the slot carries it (an orphan), or it is that block's own subject.
 *
 * Orphans are candidates on every block of the slot: the subject cannot say which block they were
 * meant for, and a reminder hidden costs more than one shown twice. Likewise a block of unknown
 * subject takes only orphans and unknown-subject events, never an event another block owns.
 *
 * **Team.** Of an event's candidates, if any carries its exact team id then it shows on those and
 * on no others; otherwise every candidate keeps it. `Event.teamId` became usable for this when
 * `librusEventTeamId` started reading `VirtualClass`: a captured `HomeWorks` body names a group's
 * event with `VirtualClass` and a whole class's with `Class`, never both, exactly as a captured
 * `Timetables` body does for lessons - so an id that matches across the two names one group.
 *
 * The stage only narrows an existing candidate set, and only onto a candidate, so it can never
 * empty a slot: whatever it hides from one block, the matching candidate shows. That is what keeps
 * a whole-class event safe. Nine events in a captured week carried the whole class for a subject
 * every one of whose lessons was a group lesson; no candidate of theirs carries the class id, so
 * nothing narrows and they still show. A rule that hid on any mismatch would have lost all nine.
 *
 * It does place an orphan or an unknown-subject event that carries a group team, which the subject
 * stage alone puts on every block. That is deliberate and is where the fix pays: both
 * `VirtualClass` events in that same capture carry no `Subject` at all, so a stage that fired only
 * on subject-owned events would have changed nothing on real data. "The subject cannot say which
 * block" stops being a reason to show an event everywhere once the team says it exactly.
 *
 * An event whose team is unknown (`Event.knownTeamId` null: -1 and 0 both mean "no team") is never
 * narrowed, and neither is one weighed against a block carrying no team of its own. The block side
 * needs no such reading back: `Lesson.teamId` is written only as null or a real id, and the early
 * return has already made the event's team positive, so nothing it could be compared against is a
 * sentinel. The block is read through [LessonFull.displayTeamId], so a shifted source is matched on
 * the team it had in the slot it is drawn in and not on the one it has where it moved to.
 *
 * Two blocks of the same subject *and* the same team both show its event; there is still no
 * cancelled-versus-ran tiebreak.
 */
fun List<EventFull>.forLesson(lesson: LessonFull, dayLessons: List<LessonFull>): List<EventFull> {
    val start = lesson.displayStartTime?.value ?: return emptyList()
    val slot = dayLessons.filter {
        it.isTimetableBlock && it.displayDate == lesson.displayDate && it.displayStartTime?.value == start
    }
    val owners = slot.mapNotNull { it.knownSubjectId }.toSet()

    fun isCandidate(event: EventFull, block: LessonFull): Boolean {
        val subject = event.knownSubjectId
        return subject == null || subject !in owners || subject == block.knownSubjectId
    }

    return filter { event ->
        if (event.time?.value != start || !isCandidate(event, lesson)) return@filter false
        val team = event.knownTeamId ?: return@filter true
        val claimed = slot.any { it.displayTeamId == team && isCandidate(event, it) }
        !claimed || lesson.displayTeamId == team
    }
}
