/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.db.entity.LessonRange
import eu.mikus.edziennik.utils.models.Time

/**
 * The time a Librus HomeWorks event is filed at: its lesson's start, else the school's `TimeFrom`,
 * else no time at all.
 *
 * [lessonRange] is the caller's lookup by the event's `LessonNo`: null means no range matched, and
 * [timeFrom] is read instead. It takes the looked-up range rather than `data.lessonRanges` because
 * a `SparseArray` is a silent stub on the JVM under `returnDefaultValues = true`.
 *
 * A null return renders "cały dzień" and exports to the calendar as all-day, which is what null
 * already means in this column: `LibrusSynergiaHomework` writes it when the date is unknown or no
 * lesson of the subject is found that day, and `LibrusApiPtMeetings` writes it when Librus sends no
 * time for a meeting. This used to read through `Time.fromH_m`, which answers 00:00 for an absent
 * or unreadable `TimeFrom`, so such an event rendered "0:00" - indistinguishable from a real
 * midnight - and exported as a timed event at midnight.
 *
 * Not a full repair. The reader takes fixed positions and validates neither separators nor ranges:
 * a digit-shaped non-time such as "25:99" still parses and renders as 25:99, and a single-digit
 * hour ("8:05") is unreadable. A literal "00:00:00" is read as midnight, not as no time - PT
 * meetings use that literal for "no time", but nothing shows that HomeWorks does.
 */
internal fun librusEventTime(lessonRange: LessonRange?, timeFrom: String?): Time? =
    lessonRange?.startTime ?: Time.fromH_mOrNull(timeFrom)
