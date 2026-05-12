/*
 * Copyright (c) Kuba Szczodrzyński 2025-11-25.
 */

package eu.mikus.edziennik.data.api.edziennik.usos.data.api

import com.google.gson.JsonArray
import eu.mikus.edziennik.data.api.ERROR_USOS_API_INCOMPLETE_RESPONSE
import eu.mikus.edziennik.data.api.edziennik.usos.DataUsos
import eu.mikus.edziennik.data.api.edziennik.usos.ENDPOINT_USOS_API_EXAMS
import eu.mikus.edziennik.data.api.edziennik.usos.data.UsosApi
import eu.mikus.edziennik.data.api.models.DataRemoveModel
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.HOUR
import eu.mikus.edziennik.ext.asJsonObjectList
import eu.mikus.edziennik.ext.getBoolean
import eu.mikus.edziennik.ext.getJsonArray
import eu.mikus.edziennik.ext.getJsonObject
import eu.mikus.edziennik.ext.getLong
import eu.mikus.edziennik.ext.getString
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

class UsosApiExams(
    override val data: DataUsos,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : UsosApi(data, lastSync) {
    companion object {
        const val TAG = "UsosApiExams"
        private const val TYPE_EXAM = 1L
        private const val TYPE_RETAKE = 11L
    }

    init {
        apiRequest<JsonArray>(
            tag = TAG,
            service = "exams/student_exams",
            fields = listOf(
                "id",
                "term",
                "course" to listOf("id", "name"),
                "name",
                "description",
                "groups" to listOf(
                    "exam_start",
                    "examiners",
                    "room" to listOf("number", "building_id"),
                ),
            ),
            responseType = ResponseType.ARRAY,
        ) { json, response ->
            if (!processResponse(json)) {
                data.error(TAG, ERROR_USOS_API_INCOMPLETE_RESPONSE, response)
                return@apiRequest
            }

            data.toRemove.add(DataRemoveModel.Events.futureWithType(TYPE_EXAM))
            data.toRemove.add(DataRemoveModel.Events.futureWithType(TYPE_RETAKE))

            data.setSyncNext(ENDPOINT_USOS_API_EXAMS, 12 * HOUR)
            onSuccess(ENDPOINT_USOS_API_EXAMS)
        }
    }

    private fun processResponse(json: JsonArray): Boolean {
        for (exam in json.asJsonObjectList()) {
            if (exam.getJsonObject("term")?.getBoolean("is_active") != true)
                continue

            val id = exam.getLong("id") ?: continue
            val course = exam.getJsonObject("course") ?: continue
            val name = exam.getLangString("name") ?: "Egzamin"
            val description = exam.getLangString("description") ?: ""
            val groups = exam.getJsonArray("groups")?.asJsonObjectList() ?: continue

            val courseName = course.getLangString("name") ?: continue
            val courseId = course.getString("id") ?: continue
            val subjectId = data.getSubject(
                id = null,
                name = courseName,
                shortName = courseId,
            ).id

            val type =
                if ("popraw" in listOfNotNull(name, description).joinToString())
                    TYPE_RETAKE
                else
                    TYPE_EXAM

            val exams = mutableListOf<Triple<Pair<Date, Time>, Long, String?>>()
            for (group in groups) {
                val examStart = group.getString("exam_start") ?: continue
                val examiners = group.getLecturerIds("examiners")
                val room = group.getJsonObject("room")

                val date = Date.fromY_m_d(examStart)
                val time = Time.fromY_m_d_H_m_s(examStart)
                val teacherId = examiners.firstOrNull() ?: -1
                val classroom = room?.let {
                    "Sala ${it.getString("number")} / bud. ${it.getString("building_id")}"
                }

                exams += Triple(date to time, teacherId, classroom)
            }

            exams.groupBy { it.first }.forEach { (dateTime, groups) ->
                var topic = name
                val classrooms = groups.mapNotNull { it.third }.joinToString(", ")
                if (classrooms.isNotEmpty())
                    topic += ": $classrooms"
                if (description.isNotEmpty())
                    topic += "\n$description"

                val eventObject = Event(
                    profileId = profileId,
                    id = id,
                    date = dateTime.first,
                    time = dateTime.second,
                    topic = topic,
                    color = null,
                    type = type,
                    teacherId = groups.first().second,
                    subjectId = subjectId,
                    teamId = data.teamClass?.id ?: -1,
                )

                data.eventList.add(eventObject)
                data.metadataList.add(
                    Metadata(
                        profileId,
                        MetadataType.EVENT,
                        id,
                        profile?.empty ?: true,
                        profile?.empty ?: true
                    )
                )
            }
        }
        return true
    }
}
