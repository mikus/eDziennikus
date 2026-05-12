/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-29.
 */

package eu.mikus.edziennik.data.api.interfaces

import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.models.Feature

/**
 * A callback passed to all [Feature]s and [LoginMethod]s
 */
interface EndpointCallback {
    fun onError(apiError: ApiError)
    fun onProgress(step: Float)
    fun onStartProgress(stringRes: Int)
}
