/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date

/**
 * The Monday of the first day after [today] that has no rows at all, or `null` if every day in the
 * next seven has some. [lessons] must already be filtered to one profile.
 *
 * This is the "is the week I am about to report on actually downloaded?" question, and it is subtler
 * than it looks. Rows exist only for dates the Librus response carried - `LibrusApiTimetables` writes
 * a NO_LESSONS marker only for keys it saw - so a day with no rows is *not* proof that its week was
 * never fetched. That inference is what made the Home card and the widget report "not downloaded"
 * with real lessons sitting two days later.
 *
 * Asking per-day and reporting that day's **week** is what makes the answer actionable: a week is
 * what a sync fetches, and it is the argument the card's Sync button passes back
 * (`HomeFragment.onTimetableSync`).
 *
 * From `today + 1`, never today itself: offering a week already behind us is how a Sync button ends
 * up unable to change anything.
 *
 * Shared by `TimetableHomeBuilder` and `WidgetTimetableProvider`, which walk forward for the next day
 * with lessons but are **not** the same walk - they differ on the first-day filter, on cancelled
 * lessons and on where the profile filter sits. Only this classification is common.
 */
fun missingWeekStart(lessons: List<LessonFull>, today: Date): String? =
    (1..7).map { today.clone().stepForward(0, 0, it) }
        .firstOrNull { d -> lessons.none { it.displayDate == d } }
        ?.weekStart?.stringY_m_d
