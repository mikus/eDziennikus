/*
 * Copyright (c) Kuba Szczodrzyński 2021-9-18.
 */

package eu.mikus.edziennik.utils.managers

import eu.mikus.edziennik.App
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.data.api.*
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.szkolny.SzkolnyApi
import eu.mikus.edziennik.data.api.szkolny.response.RegisterAvailabilityStatus
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.currentTimeUnix
import eu.mikus.edziennik.ext.toApiError

class AvailabilityManager(val app: App) {
    companion object {
        private const val TAG = "AvailabilityManager"
    }

    private val api = SzkolnyApi(app)

    data class Error(
        val type: Type,
        val status: RegisterAvailabilityStatus?,
        val apiError: ApiError?
    ) {
        companion object {
            fun notAvailable(status: RegisterAvailabilityStatus) =
                Error(Type.NOT_AVAILABLE, status, null)

            fun apiError(apiError: ApiError) =
                Error(Type.API_ERROR, null, apiError)

            fun noApiAccess() =
                Error(Type.NO_API_ACCESS, null, null)
        }

        enum class Type {
            NOT_AVAILABLE,
            API_ERROR,
            NO_API_ACCESS,
        }
    }

    fun check(profile: Profile, cacheOnly: Boolean = false): Error? {
        return check(profile.registerName, cacheOnly)
    }

    fun check(loginType: LoginType, cacheOnly: Boolean = false): Error? {
        return check(loginType.name.lowercase(), cacheOnly)
    }

    fun check(registerName: String, cacheOnly: Boolean = false): Error? {
        if (!app.config.apiAvailabilityCheck)
            return null
        val status = app.config.sync.registerAvailability[registerName]
        if (status != null && status.nextCheckAt > currentTimeUnix()) {
            return reportStatus(status)
        }
        if (cacheOnly) {
            return reportStatus(status)
        }

        return try {
            val availability = api.getRegisterAvailability()
            app.config.sync.registerAvailability = availability
            reportStatus(availability[registerName])
        } catch (e: Throwable) {
            reportApiError(e)
        }
    }

    private fun reportStatus(status: RegisterAvailabilityStatus?): Error? {
        if (status == null)
            return null
        if (!status.available || status.minVersionCode > BuildConfig.VERSION_CODE)
            return Error.notAvailable(status)
        return null
    }

    private fun reportApiError(throwable: Throwable): Error {
        val apiError = throwable.toApiError(TAG)
        if (apiError.errorCode == ERROR_API_INVALID_SIGNATURE) {
            app.config.sync.registerAvailability = mapOf()
            return Error.noApiAccess()
        }
        return Error.apiError(apiError)
    }
}
