/*
 * Copyright (c) Kacper Ziubryniewicz 2019-11-24
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Message

data class AttachmentGetEvent(val profileId: Int, val owner: Any, val attachmentId: Long,
                              var eventType: Int = TYPE_PROGRESS, val fileName: String? = null,
                              val bytesWritten: Long = 0) {
    companion object {
        const val TYPE_PROGRESS = 0
        const val TYPE_FINISHED = 1
    }

    val ownerId
        get() = when (owner) {
            is Message -> owner.id
            is Event -> owner.id
            else -> -1
        }
}
