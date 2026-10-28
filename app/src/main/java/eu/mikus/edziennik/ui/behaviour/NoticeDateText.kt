/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.ui.behaviour

import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.utils.models.Date

/**
 * The date text of a notice row, for both BehaviourScreen and NoticesAdapter: the unknown label
 * when [Notice.addedDateUnknown], else [Notice.addedDate] as `Date.formattedString`.
 *
 * Takes the notice rather than its millis so that whether the date is known stays the notice's
 * answer. Formatting the unknown value as a date would show 1970-01-01, or 1969-12-31 in a zone
 * behind UTC.
 */
internal fun noticeDateText(notice: Notice, context: Context): String = when {
    notice.addedDateUnknown -> context.getString(R.string.notice_date_unknown)
    else -> Date.fromMillis(notice.addedDate).formattedString
}
