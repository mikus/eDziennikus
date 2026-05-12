/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-19.
 */

package eu.mikus.edziennik.data.api.szkolny.request

data class WebPushRequest(
        val deviceId: String,
        val device: Device? = null,

        val action: String,

        val browserId: String? = null,
        val pairToken: String? = null
)
