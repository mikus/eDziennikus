/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-21.
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.entity.FeedbackMessage

data class FeedbackMessageEvent(val message: FeedbackMessage)
