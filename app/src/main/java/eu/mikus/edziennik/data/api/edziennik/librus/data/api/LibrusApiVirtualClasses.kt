/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-23.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_VIRTUAL_CLASSES
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.Team
import eu.mikus.edziennik.ext.*

class LibrusApiVirtualClasses(override val data: DataLibrus,
                              override val lastSync: Long?,
                              val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiVirtualClasses"
    }

    init {
        apiGet(TAG, "VirtualClasses") { json ->
            val virtualClasses = json.getJsonArray("VirtualClasses")?.asJsonObjectList()

            virtualClasses?.forEach { virtualClass ->
                val id = virtualClass.getLong("Id") ?: return@forEach
                val name = virtualClass.getString("Name") ?: ""
                val teacherId = virtualClass.getJsonObject("Teacher")?.getLong("Id") ?: -1
                val code = "${data.schoolName}:$name"

                data.teamList.put(id, Team(profileId, id, name, 2, code, teacherId))
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_VIRTUAL_CLASSES, 4* DAY)
            onSuccess(ENDPOINT_LIBRUS_API_VIRTUAL_CLASSES)
        }
    }
}
