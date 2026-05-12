/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-27
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_API_INVALID_REQUEST_PARAMS
import eu.mikus.edziennik.data.api.ERROR_LIBRUS_API_NOTICEBOARD_PROBLEM
import eu.mikus.edziennik.data.api.POST
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.api.events.AnnouncementGetEvent
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.AnnouncementFull

class LibrusApiAnnouncementMarkAsRead(override val data: DataLibrus,
                                      private val announcement: AnnouncementFull,
                                      val onSuccess: () -> Unit
) : LibrusApi(data, null) {
    companion object {
        const val TAG = "LibrusApiAnnouncementMarkAsRead"
    }

    init {
        apiGet(TAG, "SchoolNotices/MarkAsRead/${announcement.idString}", method = POST,
                ignoreErrors = listOf(
                        ERROR_LIBRUS_API_INVALID_REQUEST_PARAMS,
                        ERROR_LIBRUS_API_NOTICEBOARD_PROBLEM
                )) {
            announcement.seen = true

            EventBus.getDefault().postSticky(AnnouncementGetEvent(announcement))

            data.setSeenMetadataList.add(Metadata(
                    profileId,
                    MetadataType.ANNOUNCEMENT,
                    announcement.id,
                    announcement.seen,
                    announcement.notified
            ))
            onSuccess()
        }
    }
}
