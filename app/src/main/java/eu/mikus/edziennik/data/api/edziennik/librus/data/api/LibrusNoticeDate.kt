/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.ext.dateToSemester
import eu.mikus.edziennik.utils.models.Date

/**
 * The `addedDate` and `semester` a Librus notice is stored with.
 *
 * [librusNoticeDate] decides both in one place, from one read of the school's date: a readable
 * date gives its `inMillis` and the semester it falls in (1 without a profile), an unreadable one
 * gives [Notice.ADDED_DATE_UNKNOWN] and a placeholder semester.
 */
internal data class LibrusNoticeDate(val addedDate: Long, val semester: Int)

/**
 * Reads a notice's date, or [Notice.ADDED_DATE_UNKNOWN] when the school's date is unreadable, and
 * the semester to store with it.
 *
 * Unreadable means `Date.fromY_m_dOrNull` answers null: the value is null, shorter than ten
 * characters, or not an integer at one of the positions it reads. The parser used to skip a notice
 * whose `Date` read as null and pass any other value to `Date.fromY_m_d`, which answers 2019-01-01
 * for an unreadable one, so an unreadable string was kept and filed at 2019-01-01, a date a school
 * can really send.
 *
 * An unreadable date keeps the notice rather than skipping it. `Data.saveData` stores notices by
 * deleting the profile's rows in `notices` and inserting this sync's list, whenever that list is
 * not empty. So skipping a notice does not merely leave it out: when any other notice of the same
 * sync is kept, the save deletes any copy of the skipped one that an earlier sync stored.
 *
 * [Notice.ADDED_DATE_UNKNOWN] is the value `Notice.addedDateUnknown` recognises; that property's
 * KDoc lists the readers that ask it.
 *
 * With a [profile], an unreadable date's semester is the profile's current semester, the value a
 * notice dated today would get. It is a placeholder, needed because `noticeSemester` cannot be
 * null. No reader depends on it today: outside the DAO the only code that reads `noticeSemester`
 * is the Behaviour screen's semester filter, which keeps an undated notice whatever it holds, and
 * `NoticeDao.dontKeepSemester`, the one query that filters on the column, has no caller. Without
 * a [profile] the semester is 1, readable date or not.
 *
 * Unlike `librusEventDate` this takes no `today`: nothing here files a notice at today or compares
 * a notice's date with today. `Profile.currentSemester` reads the clock itself.
 *
 * Readable is not the same as valid: `Date.fromY_m_dOrNull` reads fixed positions and checks
 * neither the separators nor the ranges, so `"2019-99-99"` is read as a date.
 */
internal fun librusNoticeDate(raw: String?, profile: Profile?): LibrusNoticeDate =
    Date.fromY_m_dOrNull(raw)
        ?.let { LibrusNoticeDate(it.inMillis, profile?.dateToSemester(it) ?: 1) }
        ?: LibrusNoticeDate(Notice.ADDED_DATE_UNKNOWN, profile?.currentSemester ?: 1)
