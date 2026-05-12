/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-13
 */

package eu.mikus.edziennik.data.api.szkolny.request

import eu.mikus.edziennik.data.db.full.EventFull

data class EventShareRequest (
        val deviceId: String,
        val device: Device? = null,

        val action: String = "event",

        val userCode: String,
        val studentNameLong: String,

        val shareTeamCode: String? = null,
        val unshareTeamCode: String? = null,
        val requesterName: String? = null,

        val eventId: Long? = null,
        val event: EventFull? = null
)
