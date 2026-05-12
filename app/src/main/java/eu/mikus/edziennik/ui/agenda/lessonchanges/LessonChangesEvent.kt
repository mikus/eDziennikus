/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package eu.mikus.edziennik.ui.agenda.lessonchanges

import eu.mikus.edziennik.ui.agenda.BaseEvent
import eu.mikus.edziennik.utils.models.Date

class LessonChangesEvent(
    val profileId: Int,
    val date: Date,
    val count: Int,
    showBadge: Boolean
) : BaseEvent(
    id = date.value.toLong(),
    time = date.asCalendar,
    color = 0xff78909c.toInt(),
    showBadge = false,
    showItemBadge = showBadge
) {
    override fun copy() = LessonChangesEvent(profileId, date, count, showItemBadge)

    override fun getShowBadge() = false
}
