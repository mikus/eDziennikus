/*
 * Copyright (c) Kacper Ziubryniewicz 2019-10-4.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import androidx.core.util.isEmpty
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_TEACHER_FREE_DAYS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.*

class LibrusApiTeacherFreeDays(override val data: DataLibrus,
                               override val lastSync: Long?,
                               val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiTeacherFreeDays"
    }

    init {
        if (data.teacherAbsenceTypes.isEmpty()) {
            data.db.teacherAbsenceTypeDao().getAllNow(profileId).toSparseArray(data.teacherAbsenceTypes) { it.id }
        }

        apiGet(TAG, "Calendars/TeacherFreeDays") { json ->
            val teacherAbsences = json.getJsonArray("TeacherFreeDays")?.asJsonObjectList()

            teacherAbsences?.forEach { teacherAbsence ->
                val teacherAbsenceObject = librusTeacherAbsence(profileId, teacherAbsence)
                        ?: return@forEach

                data.teacherAbsenceList.add(teacherAbsenceObject)
                data.metadataList.add(Metadata(
                        profileId,
                        MetadataType.TEACHER_ABSENCE,
                        teacherAbsenceObject.id,
                        true,
                        profile?.empty ?: false
                ))
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_TEACHER_FREE_DAYS, 6* HOUR, FeatureType.AGENDA)
            onSuccess(ENDPOINT_LIBRUS_API_TEACHER_FREE_DAYS)
        }
    }
}
