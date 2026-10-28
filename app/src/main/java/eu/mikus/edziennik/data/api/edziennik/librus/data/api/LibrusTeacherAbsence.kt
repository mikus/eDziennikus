/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.TeacherAbsence
import eu.mikus.edziennik.ext.getJsonObject
import eu.mikus.edziennik.ext.getLong
import eu.mikus.edziennik.ext.getString
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

/**
 * Maps one record of Librus's `TeacherFreeDays` to a [TeacherAbsence], or returns null to skip it.
 *
 * A record is skipped when its `Id`, its `Teacher.Id`, its `DateFrom` or its `DateTo` cannot be
 * read. The dates go together: a record with only one readable date is skipped whole, never filed
 * with a stand-in for the other. A `TimeFrom` / `TimeTo` that cannot be read is stored as null and
 * does not skip the record.
 *
 * The dates used to be read through `Date.fromY_m_d`, which answers 2019-01-01 for an absent or
 * unreadable value, so an unreadable date was filed as 2019-01-01. A school that really sends
 * 2019-01-01 still gets an absence on that day: only a failed read skips.
 *
 * A skip deletes nothing. `Data.saveData` writes these with `putAll(teacherAbsenceList)`,
 * whose `removeNotKept` defaults to false; no `DataRemoveModel` variant targets
 * this table; and nothing else reaches `TeacherAbsenceDao.removeNotKept`. The only DAO call that
 * deletes these rows is the `clear(profileId)` in `clearProfileData`, which runs when a profile is
 * removed and on the LAB wipe. So a skipped record is simply not written. One that no earlier sync
 * stored is not stored now. One that an earlier sync stored is left as it was - including a
 * 2019-01-01 the old read wrote into it, which stays until a later sync saves that record with
 * both dates readable, or the row is cleared. Before, the old read's 2019-01-01 reached a stored
 * row too: `dateFrom` and `dateTo` are not among `TeacherAbsenceDao`'s `skippedColumns` (only
 * `addedDate` is), so the selective update overwrote the stored date whenever the sync's data was
 * saved.
 *
 * It skips rather than storing "no date" because both date columns are `TEXT NOT NULL`. Storing
 * null would need a schema change, and SQLite can only drop a `NOT NULL` by rebuilding the table.
 *
 * Not a full repair. `Date.fromY_m_dOrNull` reads fixed positions and validates neither separators
 * nor the calendar, so a digit-shaped non-date such as "2019-99-99" still parses and is filed. And
 * `ConverterDate` reads stored dates back through `Date.fromY_m_d`, so a stored value it cannot
 * read comes back as 2019-01-01.
 */
internal fun librusTeacherAbsence(profileId: Int, json: JsonObject): TeacherAbsence? {
    val id = json.getLong("Id") ?: return null
    val teacherId = json.getJsonObject("Teacher")?.getLong("Id") ?: return null
    val dateFrom = Date.fromY_m_dOrNull(json.getString("DateFrom")) ?: return null
    val dateTo = Date.fromY_m_dOrNull(json.getString("DateTo")) ?: return null
    val timeFrom = Time.fromH_m_sOrNull(json.getString("TimeFrom"))
    val timeTo = Time.fromH_m_sOrNull(json.getString("TimeTo"))

    return TeacherAbsence(
        profileId = profileId,
        id = id,
        type = -1L,
        name = null,
        dateFrom = dateFrom,
        dateTo = dateTo,
        timeFrom = timeFrom,
        timeTo = timeTo,
        teacherId = teacherId,
    )
}
