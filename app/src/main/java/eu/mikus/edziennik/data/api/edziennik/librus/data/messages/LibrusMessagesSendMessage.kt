/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-2.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.messages

import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusMessages
import eu.mikus.edziennik.data.api.events.MessageSentEvent
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.base64Encode
import eu.mikus.edziennik.ext.getJsonObject
import eu.mikus.edziennik.ext.getLong
import eu.mikus.edziennik.ext.getString

class LibrusMessagesSendMessage(override val data: DataLibrus,
                                val recipients: Set<Teacher>,
                                val subject: String,
                                val text: String,
                                val onSuccess: () -> Unit
) : LibrusMessages(data, null) {
    companion object {
        const val TAG = "LibrusMessages"
    }

    init {
        val params = mapOf<String, Any>(
                "topic" to subject.base64Encode(),
                "message" to text.base64Encode(),
                "receivers" to recipients
                        .filter { it.loginId != null }
                        .joinToString(",") { it.loginId ?: "" },
                "actions" to "<Actions/>".base64Encode()
        )

        messagesGetJson(TAG, "SendMessage", parameters = params) { json ->

            val response = json.getJsonObject("response").getJsonObject("SendMessage")
            val id = response.getLong("data")

            if (response.getString("status") != "ok" || id == null) {
                // val message = response.getString("message")
                // TODO error
                return@messagesGetJson
            }

            LibrusMessagesGetList(data, type = Message.TYPE_SENT, lastSync = null) {
                val message = data.messageList.firstOrNull { it.isSent && it.id == id }
                // val metadata = data.metadataList.firstOrNull { it.thingType == Metadata.TYPE_MESSAGE && it.thingId == message?.id }
                val event = MessageSentEvent(data.profileId, message, message?.addedDate)

                EventBus.getDefault().postSticky(event)
                onSuccess()
            }
        }
    }
}
