/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-25.
 */
package eu.mikus.edziennik.data.db.full

import eu.mikus.edziennik.data.db.entity.LuckyNumber
import eu.mikus.edziennik.utils.models.Date

class LuckyNumberFull(
        profileId: Int, date: Date,
        number: Int
) : LuckyNumber(
        profileId, date,
        number
) {
    // metadata
    var seen = false
    var notified = false
}
