package eu.mikus.edziennik.data.db.full

import androidx.room.Ignore
import eu.mikus.edziennik.data.db.entity.MessageRecipient

class MessageRecipientFull(
        profileId: Int,
        id: Long,
        messageId: Long,
        readDate: Long = -1L
) : MessageRecipient(profileId, id, -1, readDate, messageId) {
    @Ignore
    var fullName: String? = null
}
