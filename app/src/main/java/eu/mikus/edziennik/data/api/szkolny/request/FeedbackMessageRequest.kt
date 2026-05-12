/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-21.
 */

package eu.mikus.edziennik.data.api.szkolny.request

data class FeedbackMessageRequest(
        val deviceId: String,
        val device: Device? = null,

        val senderName: String?,
        val targetDeviceId: String?,
        val text: String
)
