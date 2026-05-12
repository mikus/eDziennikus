/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package eu.mikus.edziennik.ui.agenda.event

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ui.agenda.BaseEvent

class AgendaEvent(
    val event: EventFull,
    showBadge: Boolean = !event.seen
) : BaseEvent(
    id = event.id,
    time = event.startTimeCalendar,
    color = event.eventColor,
    showBadge = showBadge
) {
    override fun copy() = AgendaEvent(event, showBadge)
}
