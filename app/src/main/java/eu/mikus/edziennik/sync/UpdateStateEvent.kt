/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-22.
 */

package eu.mikus.edziennik.sync

import eu.mikus.edziennik.data.api.models.Update

class UpdateStateEvent(val running: Boolean, val update: Update?, val downloadId: Long)
