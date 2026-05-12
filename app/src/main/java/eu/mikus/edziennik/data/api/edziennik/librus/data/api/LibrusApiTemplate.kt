/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-4.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi

class LibrusApiTemplate(override val data: DataLibrus,
                        override val lastSync: Long?,
                        val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApi"
    }

    init {
        /*apiGet(TAG, "") { json ->

            data.setSyncNext(ENDPOINT_LIBRUS_API_, SYNC_ALWAYS)
            onSuccess(ENDPOINT_LIBRUS_API_)
        }*/
    }
}
