/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-4.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import androidx.core.util.isEmpty
import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_EVENTS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.api.models.DataRemoveModel
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.entity.SYNC_ALWAYS
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

class LibrusApiEvents(override val data: DataLibrus,
                      override val lastSync: Long?,
                      val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiEvents"
    }

    init {
        if (data.eventTypes.isEmpty()) {
            data.db.eventTypeDao().getAllNow(profileId).toSparseArray(data.eventTypes) { it.id }
        }

        apiGet(TAG, "HomeWorks") { json ->
            val events = json.getJsonArray("HomeWorks")?.asJsonObjectList()

            events?.forEach { event ->
                val id = event.getLong("Id") ?: return@forEach
                val eventDate = Date.fromY_m_d(event.getString("Date"))
                var topic = event.getString("Content")?.trim() ?: ""
                val type = event.getJsonObject("Category")?.getLong("Id") ?: -1
                val teacherId = event.getJsonObject("CreatedBy")?.getLong("Id") ?: -1
                val subjectId = event.getJsonObject("Subject")?.getLong("Id") ?: -1
                val teamId = event.getJsonObject("Class")?.getLong("Id") ?: -1

                val lessonNo = event.getInt("LessonNo")
                val lessonRange = data.lessonRanges.singleOrNull { it.lessonNumber == lessonNo }
                val startTime = lessonRange?.startTime ?: Time.fromH_m(event.getString("TimeFrom"))
                val addedDate = Date.fromIso(event.getString("AddDate"))

                event.getJsonObject("onlineLessonUrl")?.let { onlineLesson ->
                    val text = onlineLesson.getString("text")?.let { "$it - " } ?: ""
                    val url = onlineLesson.getString("url")
                    topic += "\n\n$text$url"
                }

                val eventObject = Event(
                        profileId = profileId,
                        id = id,
                        date = eventDate,
                        time = startTime,
                        topic = topic,
                        color = null,
                        type = type,
                        teacherId = teacherId,
                        subjectId = subjectId,
                        teamId = teamId,
                        addedDate = addedDate
                )

                data.eventList.add(eventObject)
                data.metadataList.add(
                        Metadata(
                                profileId,
                                MetadataType.EVENT,
                                id,
                                profile?.empty ?: false,
                                profile?.empty ?: false
                        ))
            }

            data.toRemove.add(DataRemoveModel.Events.futureExceptTypes(listOf(
                    Event.TYPE_HOMEWORK,
                    Event.TYPE_PT_MEETING
            )))

            data.setSyncNext(ENDPOINT_LIBRUS_API_EVENTS, SYNC_ALWAYS)
            onSuccess(ENDPOINT_LIBRUS_API_EVENTS)
        }
    }
}
