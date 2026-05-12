/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-28.
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.api.models.ApiError

class ApiTaskErrorEvent(val error: ApiError)
