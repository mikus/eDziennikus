/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-2.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.messages

import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.data.api.ERROR_MESSAGE_NOT_SENT_SERVER_REFUSED
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusMessages
import eu.mikus.edziennik.data.api.events.MessageSentEvent
import eu.mikus.edziennik.data.api.models.ApiError
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
                // Not a bare `return@messagesGetJson`: neither an error nor `onSuccess()` ran, so no
                // EdziennikCallback fired and ApiService never cleared the task - the send hung with
                // the notification, the spinner and the subtitle stuck, and the dataSync foreground
                // service never stopped. Code 11, not ERROR_MESSAGE_NOT_SENT: that one's reason
                // string says the message was sent but not found in the sent list, which is what
                // MessagesComposeFragment reports. Here the send was never confirmed - a non-`ok`
                // status means it was refused, and an unreadable `data` id means a response we
                // cannot interpret either way. Attaching the response is a devMode breadcrumb for
                // ApiError.toString(); nothing renders it to the user.
                //
                // Deliberately no `onSuccess()` alongside it, unlike some siblings here: that would
                // call completed() after clearTask() had already reset taskProfileId, posting a
                // second terminal event against a stale profile.
                data.error(ApiError(TAG, ERROR_MESSAGE_NOT_SENT_SERVER_REFUSED).withApiResponse(json))
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
