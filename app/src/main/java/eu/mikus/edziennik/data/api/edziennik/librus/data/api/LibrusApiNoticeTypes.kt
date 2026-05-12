/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-24.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_NOTICE_TYPES
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.NoticeType
import eu.mikus.edziennik.ext.*

class LibrusApiNoticeTypes(override val data: DataLibrus,
                           override val lastSync: Long?,
                           val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiNoticeTypes"
    }

    init {
        apiGet(TAG, "Notes/Categories") { json ->
            val noticeTypes = json.getJsonArray("Categories")?.asJsonObjectList()

            noticeTypes?.forEach { noticeType ->
                val id = noticeType.getLong("Id") ?: return@forEach
                val name = noticeType.getString("CategoryName") ?: ""

                data.noticeTypes.put(id, NoticeType(profileId, id, name))
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_NOTICE_TYPES, 4* DAY)
            onSuccess(ENDPOINT_LIBRUS_API_NOTICE_TYPES)
        }
    }
}
