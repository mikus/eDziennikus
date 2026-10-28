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

/**
 * The time a Librus PT meeting is filed at: the endpoint's `Time`, or no time at all.
 *
 * Null means no time, as it does for [librusEventTime]: it renders "cały dzień" and exports to the
 * calendar as all-day. Librus's literal "00:00:00" is read as null, as the PT meetings parser read
 * it before this function existed. Only this reader does that: [librusEventTime] keeps the literal
 * as midnight, because the PT meetings parser is the only one that read it as "no time", and
 * nothing shows that HomeWorks means it that way (pinned by
 * `LibrusEventTimeTest > a literal midnight TimeFrom is kept as midnight`).
 *
 * This used to read through `Time.fromH_m_s`, which answers 00:00:00 for an empty or unreadable
 * value, so such a meeting rendered "0:00" and exported as a timed event at midnight. An unreadable
 * value is now null too. That is the bucket the literal already lands in, and 00:00:00 is the value
 * the old reader's failures came out as, so no new meaning is invented.
 *
 * Not a full repair. The reader takes fixed positions and validates neither separators nor ranges:
 * a digit-shaped non-time such as "25:99:00" still parses and renders as 25:99, and a time sent
 * without seconds ("17:30") is unreadable.
 */
internal fun librusPtMeetingTime(raw: String?): Time? =
    raw?.let { if (it == "00:00:00") null else Time.fromH_m_sOrNull(it) }
