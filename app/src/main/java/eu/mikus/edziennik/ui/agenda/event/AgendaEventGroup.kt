/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-10.
 */

package eu.mikus.edziennik.ui.agenda.event

import eu.mikus.edziennik.ui.agenda.BaseEvent
import eu.mikus.edziennik.utils.models.Date

class AgendaEventGroup(
    val profileId: Int,
    val date: Date,
    val typeId: Long,
    val typeName: String,
    val typeColor: Int,
    val count: Int,
    showBadge: Boolean
) : BaseEvent(
    id = date.value.toLong(),
    time = date.asCalendar,
    color = typeColor,
    showBadge = showBadge
) {
    override fun copy() = AgendaEventGroup(profileId, date, typeId, typeName, typeColor, count, showBadge)
}
