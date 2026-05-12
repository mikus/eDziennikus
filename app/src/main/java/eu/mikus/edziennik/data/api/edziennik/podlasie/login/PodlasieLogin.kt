/*
 * Copyright (c) Kacper Ziubryniewicz 2020-5-12
 */

package eu.mikus.edziennik.data.api.edziennik.podlasie.login

import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.podlasie.DataPodlasie
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.utils.Utils

class PodlasieLogin(val data: DataPodlasie, val onSuccess: () -> Unit) {
    companion object {
        const val TAG = "PodlasieLogin"
    }

    private var cancelled = false

    init {
        nextLoginMethod(onSuccess)
    }

    private fun nextLoginMethod(onSuccess: () -> Unit) {
        if (data.targetLoginMethods.isEmpty()) {
            onSuccess()
            return
        }
        if (cancelled) {
            onSuccess()
            return
        }
        useLoginMethod(data.targetLoginMethods.removeAt(0)) { usedMethod ->
            data.progress(data.progressStep)
            if (usedMethod != null)
                data.loginMethods.add(usedMethod)
            nextLoginMethod(onSuccess)
        }
    }

    private fun useLoginMethod(loginMethod: LoginMethod, onSuccess: (usedMethod: LoginMethod?) -> Unit) {
        // this should never be true
        if (data.loginMethods.contains(loginMethod)) {
            onSuccess(null)
            return
        }
        Utils.d(TAG, "Using login method $loginMethod")
        when (loginMethod) {
            LoginMethod.PODLASIE_API -> {
                data.startProgress(R.string.edziennik_progress_login_podlasie_api)
                PodlasieLoginApi(data) { onSuccess(loginMethod) }
            }
            else -> {}
        }
    }
}
