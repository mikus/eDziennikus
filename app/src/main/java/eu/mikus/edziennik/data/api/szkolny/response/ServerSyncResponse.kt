/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-8
 */

package eu.mikus.edziennik.data.api.szkolny.response

import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.full.EventFull

data class ServerSyncResponse(
        val events: List<EventFull>,
        val notes: List<Note>
)
