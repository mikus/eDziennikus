/*
 * Copyright (c) Kacper Ziubryniewicz 2019-10-13
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_ANNOUNCEMENTS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.Announcement
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.entity.SYNC_ALWAYS
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.utils.models.Date

class LibrusApiAnnouncements(override val data: DataLibrus,
                             override val lastSync: Long?,
                             val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiAnnouncements"
    }

    init { data.profile?.also { profile ->
        apiGet(TAG, "SchoolNotices") { json ->
            val announcements = json.getJsonArray("SchoolNotices")?.asJsonObjectList()

            announcements?.forEach { announcement ->
                val longId = announcement.getString("Id") ?: return@forEach
                val id = longId.crc32()
                val subject = announcement.getString("Subject") ?: ""
                val text = announcement.getString("Content") ?: ""
                val startDate = Date.fromY_m_d(announcement.getString("StartDate"))
                val endDate = Date.fromY_m_d(announcement.getString("EndDate"))
                val teacherId = announcement.getJsonObject("AddedBy")?.getLong("Id") ?: -1
                val addedDate = announcement.getString("CreationDate")?.let { Date.fromIso(it) }
                        ?: System.currentTimeMillis()
                val read = announcement.getBoolean("WasRead") ?: false

                val announcementObject = Announcement(
                        profileId = profileId,
                        id = id,
                        subject = subject,
                        text = text,
                        startDate = startDate,
                        endDate = endDate,
                        teacherId = teacherId,
                        addedDate = addedDate
                ).also {
                    it.idString = longId
                }

                data.announcementList.add(announcementObject)
                data.setSeenMetadataList.add(Metadata(
                        profileId,
                        MetadataType.ANNOUNCEMENT,
                        id,
                        read,
                        profile.empty || read
                ))
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_ANNOUNCEMENTS, SYNC_ALWAYS)
            onSuccess(ENDPOINT_LIBRUS_API_ANNOUNCEMENTS)
        }
    }}
}
