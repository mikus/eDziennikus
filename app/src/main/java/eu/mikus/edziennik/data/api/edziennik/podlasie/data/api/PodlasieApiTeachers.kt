/*
 * Copyright (c) Kacper Ziubryniewicz 2020-5-13
 */

package eu.mikus.edziennik.data.api.edziennik.podlasie.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.data.api.edziennik.podlasie.DataPodlasie
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getLong
import eu.mikus.edziennik.ext.getString

class PodlasieApiTeachers(val data: DataPodlasie, val rows: List<JsonObject>) {
    init {
        rows.forEach { teacher ->
            val id = teacher.getLong("ExternalId") ?: return@forEach
            val firstName = teacher.getString("FirstName") ?: return@forEach
            val lastName = teacher.getString("LastName") ?: return@forEach
            val isEducator = teacher.getInt("Educator") == 1

            val teacherObject = Teacher(
                    profileId = data.profileId,
                    id = id,
                    name = firstName,
                    surname = lastName,
                    loginId = null
            )

            data.teacherList.put(id, teacherObject)

            val teamClass = data.teamClass
            if (isEducator && teamClass != null) {
                data.teamList.put(teamClass.id, teamClass.apply {
                    teacherId = id
                })
            }
        }
    }
}
