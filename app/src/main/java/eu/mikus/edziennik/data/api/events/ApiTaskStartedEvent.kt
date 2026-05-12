/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-28.
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.entity.Profile

class ApiTaskStartedEvent(val profileId: Int, val profile: Profile? = null)
