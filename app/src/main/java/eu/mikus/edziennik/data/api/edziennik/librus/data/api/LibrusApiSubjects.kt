/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-23.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_SUBJECTS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.Subject
import eu.mikus.edziennik.ext.*

class LibrusApiSubjects(override val data: DataLibrus,
                        override val lastSync: Long?,
                        val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiSubjects"
    }

    init {
        apiGet(TAG, "Subjects") { json ->
            val subjects = json.getJsonArray("Subjects")?.asJsonObjectList()

            subjects?.forEach { subject ->
                val id = subject.getLong("Id") ?: return@forEach
                val longName = subject.getString("Name") ?: ""
                val shortName = subject.getString("Short") ?: ""

                data.subjectList.put(id, Subject(profileId, id, longName, shortName))
            }

            data.subjectList.put(1, Subject(profileId, 1, "Zachowanie", "zach"))

            data.setSyncNext(ENDPOINT_LIBRUS_API_SUBJECTS, 4* DAY)
            onSuccess(ENDPOINT_LIBRUS_API_SUBJECTS)
        }
    }
}
