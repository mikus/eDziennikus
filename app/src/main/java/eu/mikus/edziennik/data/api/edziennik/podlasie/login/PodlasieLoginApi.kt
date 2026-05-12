/*
 * Copyright (c) Kacper Ziubryniewicz 2020-5-12
 */

package eu.mikus.edziennik.data.api.edziennik.podlasie.login

import eu.mikus.edziennik.data.api.ERROR_LOGIN_DATA_MISSING
import eu.mikus.edziennik.data.api.edziennik.podlasie.DataPodlasie
import eu.mikus.edziennik.data.api.models.ApiError

class PodlasieLoginApi(val data: DataPodlasie, val onSuccess: () -> Unit) {
    companion object {
        const val TAG = "PodlasieLoginApi"
    }

    init { run {
        if (data.isApiLoginValid()) {
            onSuccess()
        } else {
            data.error(ApiError(TAG, ERROR_LOGIN_DATA_MISSING))
        }
    }}
}
