/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_LESSONS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.LibrusLesson
import eu.mikus.edziennik.ext.*

class LibrusApiLessons(override val data: DataLibrus,
                       override val lastSync: Long?,
                       val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiLessons"
    }

    init {
        apiGet(TAG, "Lessons") { json ->
            val lessons = json.getJsonArray("Lessons")?.asJsonObjectList()

            lessons?.forEach { lesson ->
                val id = lesson.getLong("Id") ?: return@forEach
                val teacherId = lesson.getJsonObject("Teacher")?.getLong("Id") ?: return@forEach
                val subjectId = lesson.getJsonObject("Subject")?.getLong("Id") ?: return@forEach
                val teamId = lesson.getJsonObject("Class")?.getLong("Id")

                val librusLesson = LibrusLesson(
                        profileId,
                        id,
                        teacherId,
                        subjectId,
                        teamId
                )

                data.librusLessons.put(id, librusLesson)
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_LESSONS, 4* DAY)
            onSuccess(ENDPOINT_LIBRUS_API_LESSONS)
        }
    }
}
