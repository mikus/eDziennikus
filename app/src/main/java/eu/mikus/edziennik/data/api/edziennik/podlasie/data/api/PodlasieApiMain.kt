/*
 * Copyright (c) Kacper Ziubryniewicz 2020-5-12
 */

package eu.mikus.edziennik.data.api.edziennik.podlasie.data.api

import eu.mikus.edziennik.data.api.PODLASIE_API_USER_ENDPOINT
import eu.mikus.edziennik.data.api.edziennik.podlasie.DataPodlasie
import eu.mikus.edziennik.data.api.edziennik.podlasie.ENDPOINT_PODLASIE_API_MAIN
import eu.mikus.edziennik.data.api.edziennik.podlasie.data.PodlasieApi
import eu.mikus.edziennik.data.db.entity.SYNC_ALWAYS
import eu.mikus.edziennik.ext.asJsonObjectList
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getJsonArray

class PodlasieApiMain(override val data: DataPodlasie,
                      override val lastSync: Long?,
                      val onSuccess: (endpointId: Int) -> Unit) : PodlasieApi(data, lastSync) {
    companion object {
        const val TAG = "PodlasieApiTimetable"
    }

    init {
        apiGet(TAG, PODLASIE_API_USER_ENDPOINT) { json ->
            // Save the class team when it doesn't exist.
            data.getTeam(
                id = null,
                name = data.className ?: "",
                schoolCode = data.schoolShortName ?: "",
                isTeamClass = true
            )

            json.getInt("LuckyNumber")?.let { PodlasieApiLuckyNumber(data, it) }
            json.getJsonArray("Teacher")?.asJsonObjectList()?.let { PodlasieApiTeachers(data, it) }
            json.getJsonArray("Timetable")?.asJsonObjectList()?.let { PodlasieApiTimetable(data, it) }
            json.getJsonArray("Marks")?.asJsonObjectList()?.let { PodlasieApiGrades(data, it) }
            json.getJsonArray("MarkFinal")?.asJsonObjectList()?.let { PodlasieApiFinalGrades(data, it) }
            json.getJsonArray("News")?.asJsonObjectList()?.let { PodlasieApiEvents(data, it) }
            json.getJsonArray("Tasks")?.asJsonObjectList()?.let { PodlasieApiHomework(data, it) }

            data.setSyncNext(ENDPOINT_PODLASIE_API_MAIN, SYNC_ALWAYS)
            onSuccess(ENDPOINT_PODLASIE_API_MAIN)
        }
    }
}
