/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-21.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_PUSH_CONFIG
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.SYNC_ALWAYS
import eu.mikus.edziennik.ext.JsonObject
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getJsonObject

class LibrusApiPushConfig(override val data: DataLibrus,
                          override val lastSync: Long?,
                          val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiPushConfig"
    }

    init { data.app.config.sync.tokenLibrus?.also { tokenLibrus ->
        if(tokenLibrus.isEmpty()) {
            data.setSyncNext(ENDPOINT_LIBRUS_API_PUSH_CONFIG, SYNC_ALWAYS)
            data.app.config.sync.tokenLibrusList =
                    data.app.config.sync.tokenLibrusList + profileId
            onSuccess(ENDPOINT_LIBRUS_API_PUSH_CONFIG)
            return@also
        }

        apiGet(TAG, "ChangeRegister", payload = JsonObject(
                "provider" to "FCM",
                "device" to tokenLibrus,
                "sendPush" to "1",
                "appVersion" to 4
        )) { json ->
            json.getJsonObject("ChangeRegister")?.getInt("Id")?.let { data.pushDeviceId = it }

            // sync always: this endpoint has .shouldSync set
            data.setSyncNext(ENDPOINT_LIBRUS_API_PUSH_CONFIG, SYNC_ALWAYS)
            data.app.config.sync.tokenLibrusList =
                    data.app.config.sync.tokenLibrusList + profileId
            onSuccess(ENDPOINT_LIBRUS_API_PUSH_CONFIG)
        }
    } ?: onSuccess(ENDPOINT_LIBRUS_API_PUSH_CONFIG) }
}
