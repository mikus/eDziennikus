/*
 * Copyright (c) Kuba Szczodrzyński 2021-9-18.
 */

package eu.mikus.edziennik.utils.managers

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.szkolny.response.RegisterAvailabilityStatus
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType

/**
 * Neutered after the fork dropped SzkolnyApi: the upstream availability
 * endpoint (`api.getRegisterAvailability()`) is no longer reachable, so
 * [check] always returns null and every provider is treated as available.
 *
 * The [Error] type and [check] overload signatures are preserved so the
 * ~15 caller sites (MainActivity, HomeFragment, HomeAvailabilityCard,
 * EdziennikTask, LoginChooserFragment, …) keep compiling without edits;
 * their `availabilityManager.check(…)?.let { … }` paths simply never enter.
 *
 * If a future fork wants per-provider availability reporting, this is the
 * single class to revisit — every consumer already gates on a nullable
 * [Error] result.
 */
class AvailabilityManager(@Suppress("UNUSED_PARAMETER") app: App) {
    companion object {
        private const val TAG = "AvailabilityManager"
    }

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

    @Suppress("UNUSED_PARAMETER")
    fun check(profile: Profile, cacheOnly: Boolean = false): Error? = null

    @Suppress("UNUSED_PARAMETER")
    fun check(loginType: LoginType, cacheOnly: Boolean = false): Error? = null

    @Suppress("UNUSED_PARAMETER")
    fun check(registerName: String, cacheOnly: Boolean = false): Error? = null
}
