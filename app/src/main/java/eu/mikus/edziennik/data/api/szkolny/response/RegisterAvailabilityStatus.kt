/*
 * Copyright (c) Kuba Szczodrzyński 2020-9-2.
 */

package eu.mikus.edziennik.data.api.szkolny.response

import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.ext.DAY
import eu.mikus.edziennik.ext.currentTimeUnix

data class RegisterAvailabilityStatus(
    val available: Boolean,
    val name: String?,
    val userMessage: Message?,
    val nextCheckAt: Long = currentTimeUnix() + 7 * DAY,
    val minVersionCode: Int = BuildConfig.VERSION_CODE
) {
    data class Message(
            val title: String,
            val contentShort: String,
            val contentLong: String,
            val icon: String?,
            val image: String?,
            val url: String?
    )
}
