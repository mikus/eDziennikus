/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.utils.models.Date

/**
 * The date a Librus event is filed under, and whether the school actually gave it.
 *
 * When [unknown] is true, [date] is a filing date chosen by the parser, not the school's date.
 *
 * A caller must copy [unknown] onto `Event.dateUnknown` on every path, `false` included.
 * `EventDao.update` is a selective update that writes `eventDateUnknown` on every sync, so
 * copying `false` is what clears a flag an earlier sync set once the school's date becomes
 * readable. Leaving it unassigned would only look right because the field starts false.
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
