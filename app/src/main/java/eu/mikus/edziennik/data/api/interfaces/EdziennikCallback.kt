/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-29.
 */

package eu.mikus.edziennik.data.api.interfaces

import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.models.Feature

/**
 * A callback passed only to an e-register class.
 * All [Feature]s and [LoginMethod]s receive this callback,
 * but may only use [EndpointCallback]'s methods.
 */
interface EdziennikCallback : EndpointCallback {
    fun onCompleted()
    fun onRequiresUserAction(event: UserActionRequiredEvent)
}
