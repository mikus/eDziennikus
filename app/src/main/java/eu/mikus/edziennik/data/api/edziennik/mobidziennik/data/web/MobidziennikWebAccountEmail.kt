/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-21.
 */

package eu.mikus.edziennik.data.api.edziennik.mobidziennik.data.web

import eu.mikus.edziennik.data.api.Regexes
import eu.mikus.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import eu.mikus.edziennik.data.api.edziennik.mobidziennik.ENDPOINT_MOBIDZIENNIK_WEB_ACCOUNT_EMAIL
import eu.mikus.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import eu.mikus.edziennik.ext.DAY
import eu.mikus.edziennik.ext.get
import eu.mikus.edziennik.ext.isNotNullNorBlank

class MobidziennikWebAccountEmail(override val data: DataMobidziennik,
                                  override val lastSync: Long?,
                                  val onSuccess: (endpointId: Int) -> Unit
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikWebAccountEmail"
    }

    init {
        webGet(TAG, "/dziennik/edytujprofil") { text ->
            MobidziennikLuckyNumberExtractor(data, text)

            val email = Regexes.MOBIDZIENNIK_ACCOUNT_EMAIL.find(text)?.let { it[1] }
            if (email.isNotNullNorBlank())
                data.loginEmail = email

            data.setSyncNext(ENDPOINT_MOBIDZIENNIK_WEB_ACCOUNT_EMAIL, if (email == null) 3* DAY else 7* DAY)
            onSuccess(ENDPOINT_MOBIDZIENNIK_WEB_ACCOUNT_EMAIL)
        }
    }
}
