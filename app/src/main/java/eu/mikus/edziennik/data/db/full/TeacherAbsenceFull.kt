/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-25.
 */
package eu.mikus.edziennik.data.db.full

import eu.mikus.edziennik.data.db.entity.TeacherAbsence
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

class TeacherAbsenceFull(
        profileId: Int, id: Long, type: Long, name: String?,
        dateFrom: Date, dateTo: Date, timeFrom: Time?, timeTo: Time?,
        teacherId: Long, addedDate: Long = System.currentTimeMillis()
) : TeacherAbsence(
        profileId, id, type, name,
        dateFrom, dateTo, timeFrom, timeTo,
        teacherId, addedDate
) {
    var teacherName: String? = null

    // metadata
    var seen: Boolean = false
    var notified: Boolean = false
}
