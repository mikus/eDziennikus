/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-24.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_CLASSROOMS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.Classroom
import eu.mikus.edziennik.ext.*
import java.util.*

class LibrusApiClassrooms(override val data: DataLibrus,
                          override val lastSync: Long?,
                          val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiClassrooms"
    }

    init {
        apiGet(TAG, "Classrooms") { json ->
            val classrooms = json.getJsonArray("Classrooms")?.asJsonObjectList()

            classrooms?.forEach { classroom ->
                val id = classroom.getLong("Id") ?: return@forEach
                val name = classroom.getString("Name")?.lowercase() ?: ""
                val symbol = classroom.getString("Symbol")?.lowercase() ?: ""
                val nameShort = name.fixWhiteSpaces().split(" ").onEach { it[0] }.joinToString()
                val symbolParts = symbol.fixWhiteSpaces().split(" ")

                val friendlyName = if (name != symbol && !name.contains(symbol) && !name.containsAll(symbolParts) && !nameShort.contains(symbol)) {
                    classroom.getString("Symbol") + " " + classroom.getString("Name")
                }
                else {
                    classroom.getString("Name") ?: ""
                }

                data.classrooms.put(id, Classroom(profileId, id, friendlyName))
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_CLASSROOMS, 4* DAY)
            onSuccess(ENDPOINT_LIBRUS_API_CLASSROOMS)
        }
    }
}
