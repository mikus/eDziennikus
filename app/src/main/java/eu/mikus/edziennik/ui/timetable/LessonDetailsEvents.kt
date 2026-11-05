/*
 * Copyright (c) Mikolaj Olszewski 2026-11-5.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The events the lesson dialog shows for [lesson]: [events] (matched on date and time only) narrowed
 * by [forLesson], the same rule the timetable screen and the widget apply.
 *
 * [combine] waits for both flows, so until the day's lessons have loaded nothing is emitted and the
 * first frame can never show every event of a shared slot.
 */
fun lessonEventsFlow(
    lesson: LessonFull,
    events: Flow<List<EventFull>>,
    dayLessons: Flow<List<LessonFull>>,
): Flow<List<EventFull>> = combine(events, dayLessons) { e, d -> e.forLesson(lesson, d) }
