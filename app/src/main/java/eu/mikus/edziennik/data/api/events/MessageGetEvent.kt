/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-12.
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.full.MessageFull

data class MessageGetEvent(val message: MessageFull)
