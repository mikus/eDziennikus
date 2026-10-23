/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

/**
 * The date a Librus event is filed under, and whether the school actually gave it.
 *
 * When [unknown] is true, [date] is a filing date chosen by the parser, not the school's date.
 *
 * [librusEvent] copies [unknown] onto `Event.dateUnknown` on every path, `false` included, so a
 * caller building an event from this value never does it by hand. See [librusEvent] for why
 * copying `false` matters.
 */
internal data class LibrusEventDate(val date: Date, val unknown: Boolean)

/**
 * Reads an event's date, or files it at [today] and says so when the school's date is unreadable.
 *
 * Unreadable means null, empty, truncated or malformed. This used to read through
 * `Date.fromY_m_d`, which answers 2019-01-01 for all of those, and a school that really says
 * 2019-01-01 is indistinguishable from one that said nothing.
 *
 * The fallback is [today] and not a sentinel because two readers treat the date as a position in
 * time, and a date in the past is wrong for both. The `seen` computation in
 * `LibrusSynergiaHomework` is `eventDate < today`, so a past date marks the homework seen and
 * notified: no notification, no unread badge. `HomeworkViewModel.classify` puts a homework in
 * the Current tab only when `event.date >= todayDate`, so a past date hides it there too. Filed
 * at today the record stays unseen and notifiable, and because `eventDate` is not among the
 * selective update's `skippedColumns`, every sync that still sees the record re-files it at that
 * day - so it keeps its place in the Current tab and inside the `eventDate >= today` window that
 * `DataRemoveModel.Events` marks `keep = 0` before each sync.
 *
 * Those last two hold only while the school keeps sending it. The filing date is frozen at the
 * last sync that saw the record, while `DataRemoveModel.Events.commit` re-reads `Date.getToday()`
 * at prune time, so a record the school stops sending is pruned only if it disappears on its
 * filing day; across a midnight the mark pass skips it, `keep` stays 1, and nothing deletes it.
 * That is no worse than the 2019-01-01 this replaces, which never matched the window at all - but
 * it is not a cure for it either.
 * [unknown] is what tells the UI the date is not the school's.
 *
 * [today] is a parameter so that a caller which also compares against today can hold one clock
 * read for both. Two reads of `Date.getToday()` can straddle midnight.
 */
internal fun librusEventDate(raw: String?, today: Date): LibrusEventDate =
    Date.fromY_m_dOrNull(raw)?.let { LibrusEventDate(it, unknown = false) }
        ?: LibrusEventDate(today, unknown = true)

/**
 * Builds the `Event` a Librus parser files for [eventDate], copying [LibrusEventDate.unknown]
 * onto `Event.dateUnknown` as part of construction.
 *
 * It takes the whole [LibrusEventDate] rather than its date so the two halves cannot be pulled
 * apart: a caller that has the pair has already handed over the flag, and there is no second
 * statement left to forget. `dateUnknown` is a body property of `Event`, not a constructor
 * parameter, so building the entity by hand leaves the write optional, and deleting it looks like
 * tidying a redundant assignment to the default.
 *
 * The flag is written on every path, `false` included. The generated selective `EventDao.update`
 * binds `eventDateUnknown` on every sync, and the column is not among its `skippedColumns`, so an
 * event built without the write does not merely miss the flag - it clears one an earlier sync
 * set. Copying `false` is likewise what clears that flag once the school's date becomes readable;
 * leaving it unassigned would only look right because the field starts false.
 *
 * This is a free function and not a member of [LibrusEventDate] on purpose. A two-field value
 * object should not know how to assemble the whole entity; the factory is a Pure Fabrication
 * that carries the construction rule so the value object can stay a pair.
 *
 * No parameter has a default, so a parser cannot silently fall back on the entity's.
 */
internal fun librusEvent(
    profileId: Int,
    id: Long,
    eventDate: LibrusEventDate,
    time: Time?,
    topic: String,
    color: Int?,
    type: Long,
    teacherId: Long,
    subjectId: Long,
    teamId: Long,
    addedDate: Long,
): Event = Event(
    profileId = profileId,
    id = id,
    date = eventDate.date,
    time = time,
    topic = topic,
    color = color,
    type = type,
    teacherId = teacherId,
    subjectId = subjectId,
    teamId = teamId,
    addedDate = addedDate,
).also {
    it.dateUnknown = eventDate.unknown
}
