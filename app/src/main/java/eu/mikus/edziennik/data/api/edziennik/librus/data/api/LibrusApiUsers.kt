/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-23.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_USERS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.*

class LibrusApiUsers(override val data: DataLibrus,
                     override val lastSync: Long?,
                     val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiUsers"
    }

    init {
        apiGet(TAG, "Users") { json ->
            val users = json.getJsonArray("Users")?.asJsonObjectList()

            users?.forEach { user ->
                val id = user.getLong("Id") ?: return@forEach
                val firstName = user.getString("FirstName")?.fixName() ?: ""
                val lastName = user.getString("LastName")?.fixName() ?: ""

                val teacher = Teacher(profileId, id, firstName, lastName)

                if (user.getBoolean("IsSchoolAdministrator") == true)
                    teacher.setTeacherType(Teacher.TYPE_SCHOOL_ADMIN)
                if (user.getBoolean("IsPedagogue") == true)
                    teacher.setTeacherType(Teacher.TYPE_PEDAGOGUE)

                data.teacherList.put(id, teacher)
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_USERS, 4* DAY)
            onSuccess(ENDPOINT_LIBRUS_API_USERS)
        }
    }
}
