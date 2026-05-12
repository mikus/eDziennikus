/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-5.
 */

package eu.mikus.edziennik.data.api.edziennik.template.login

import eu.mikus.edziennik.data.api.ERROR_LOGIN_DATA_MISSING
import eu.mikus.edziennik.data.api.ERROR_PROFILE_MISSING
import eu.mikus.edziennik.data.api.edziennik.template.DataTemplate
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.ext.HOUR
import eu.mikus.edziennik.ext.currentTimeUnix

class TemplateLoginApi(val data: DataTemplate, val onSuccess: () -> Unit) {
    companion object {
        private const val TAG = "TemplateLoginApi"
    }

    init { run {
        if (data.profile == null) {
            data.error(ApiError(TAG, ERROR_PROFILE_MISSING))
            return@run
        }

        if (data.isApiLoginValid()) {
            onSuccess()
        }
        else {
            if (/*data.webLogin != null && data.webPassword != null && */true) {
                loginWithCredentials()
            }
            else {
                data.error(ApiError(TAG, ERROR_LOGIN_DATA_MISSING))
            }
        }
    }}

    fun loginWithCredentials() {
        // succeed immediately

        data.apiToken = "ThisIsAVeryLongToken"
        data.apiExpiryTime = currentTimeUnix() + 24 * HOUR
        onSuccess()
    }
}
