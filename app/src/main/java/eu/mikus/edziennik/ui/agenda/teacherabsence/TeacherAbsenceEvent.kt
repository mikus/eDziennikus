/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package eu.mikus.edziennik.ui.agenda.teacherabsence

import eu.mikus.edziennik.ui.agenda.BaseEvent
import eu.mikus.edziennik.utils.models.Date

class TeacherAbsenceEvent(
    val profileId: Int,
    val date: Date,
    val count: Int
) : BaseEvent(
    id = date.value.toLong(),
    time = date.asCalendar,
    color = 0xffff1744.toInt(),
    showBadge = false
) {
    override fun copy() = TeacherAbsenceEvent(profileId, date, count)
}
