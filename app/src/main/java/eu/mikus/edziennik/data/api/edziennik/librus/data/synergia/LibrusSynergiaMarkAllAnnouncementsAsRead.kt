/*
 * Copyright (c) Kacper Ziubryniewicz 2019-10-26
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.synergia

import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusSynergia
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.MetadataType

class LibrusSynergiaMarkAllAnnouncementsAsRead(override val data: DataLibrus,
                                               val onSuccess: () -> Unit
) : LibrusSynergia(data, null) {
    companion object {
        const val TAG = "LibrusSynergiaMarkAllAnnouncementsAsRead"
    }

    init {
        synergiaGet(TAG, "ogloszenia") {
            data.app.db.metadataDao().setAllSeen(profileId, MetadataType.ANNOUNCEMENT, true)
            onSuccess()
        }
    }
}
