/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-31.
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.full.EventFull

data class EventGetEvent(val event: EventFull)
