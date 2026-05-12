/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-11.
 */

package eu.mikus.edziennik.data.api.edziennik.mobidziennik.data.web

import eu.mikus.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import eu.mikus.edziennik.data.api.edziennik.mobidziennik.ENDPOINT_MOBIDZIENNIK_WEB_NOTICES
import eu.mikus.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import eu.mikus.edziennik.data.db.entity.SYNC_ALWAYS

class MobidziennikWebNotices(override val data: DataMobidziennik,
                             override val lastSync: Long?,
                             val onSuccess: (endpointId: Int) -> Unit
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikWebNotices"
    }

    init {
        // TODO this does no longer work: Mobidziennik changed their mobile page in 2019.09
        data.setSyncNext(ENDPOINT_MOBIDZIENNIK_WEB_NOTICES, SYNC_ALWAYS)
        onSuccess(ENDPOINT_MOBIDZIENNIK_WEB_NOTICES)
        /*webGet(TAG, "/mobile/zachowanie") { text ->
            MobidziennikLuckyNumberExtractor(data, text)

            data.setSyncNext(ENDPOINT_MOBIDZIENNIK_WEB_NOTICES, SYNC_ALWAYS)
            onSuccess()
        }*/
    }
}
