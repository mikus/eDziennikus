/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.ui.widgets.timetable

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.LessonFull

/** What the widget renders for the day its walk resolved. */
enum class WidgetDayState { CONTENT, NOT_DOWNLOADED, NO_LESSONS }

/**
 * Two different questions, which must not be conflated: [resolved] is what the walk found for the day
 * it settled on, and [notDownloadedWeek] is `TimetableCoverage.missingWeekStart` - a fact about the
 * *window*, not about that day.
 *
 * Coverage is consulted only when the resolved day has nothing worth showing, exactly as
 * `TimetableHomeBuilder` does. Asking it first instead reports "not downloaded" over lessons the walk
 * had already found - and since `LibrusApiTimetables` pre-fetches next week only at weekends, the
 * window contains an unfetched day every Monday to Friday, so it would report that on every normal
 * week.
 */
fun widgetDayState(resolved: List<LessonFull>, notDownloadedWeek: String?): WidgetDayState = when {
    resolved.none { !it.isCancelled } || (resolved.size == 1 && resolved[0].type == Lesson.TYPE_NO_LESSONS) ->
        if (notDownloadedWeek != null) WidgetDayState.NOT_DOWNLOADED else WidgetDayState.NO_LESSONS
    else -> WidgetDayState.CONTENT
}
