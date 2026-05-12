/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-24.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_EVENT_TYPES
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.EventType
import eu.mikus.edziennik.ext.*

class LibrusApiEventTypes(override val data: DataLibrus,
                          override val lastSync: Long?,
                          val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiEventTypes"
    }

    init {
        apiGet(TAG, "HomeWorks/Categories") { json ->
            val eventTypes = json.getJsonArray("Categories")?.asJsonObjectList()

            eventTypes?.forEach { eventType ->
                val id = eventType.getLong("Id") ?: return@forEach
                val name = eventType.getString("Name") ?: ""
                val color = data.getColor(eventType.getJsonObject("Color")?.getInt("Id"))

                data.eventTypes.put(id, EventType(profileId, id, name, color))
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_EVENT_TYPES, 4* DAY)
            onSuccess(ENDPOINT_LIBRUS_API_EVENT_TYPES)
        }
    }
}
