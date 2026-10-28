/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.task

import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.data.db.full.NoticeFull
import eu.mikus.edziennik.utils.models.Date

/**
 * The short text of a new-notice notification.
 *
 * When [Notice.addedDateUnknown]: `notification_notice_unknown_date_format` with [typeName] and the
 * notice's `teacherName`. Else: `notification_notice_format` with [typeName], `teacherName` and
 * [Notice.addedDate] as `Date.formattedString`.
 *
 * The unknown date has a format of its own rather than a label in the date slot: the Polish format
 * puts the date after `z` ("z 1 stycznia"), a case-governed slot, and "data nieznana" is
 * nominative. So the undated format has no date slot and says the date is unknown after an em dash,
 * as the unknown-date event notification formats do.
 *
 * Unlike [eventNotificationFormat], which returns a `@StringRes` and leaves its caller to pass the
 * arguments, this returns the resolved text, so a test pins the arguments (three for a dated
 * notice, two for an undated one) and not only the format. Taking the notice rather than its fields
 * keeps the choice of `teacherName` in here too.
 *
 * A null `teacherName` is passed through unchanged, so either format renders it as "null" (Polish
 * "… od null …"). That is not fixed here.
 */
internal fun noticeNotificationText(
    typeName: String,
    notice: NoticeFull,
    context: Context,
): String = when {
    notice.addedDateUnknown -> context.getString(
        R.string.notification_notice_unknown_date_format,
        typeName,
        notice.teacherName,
    )
    else -> context.getString(
        R.string.notification_notice_format,
        typeName,
        notice.teacherName,
        Date.fromMillis(notice.addedDate).formattedString,
    )
}

/**
 * The time to store as a new-notice notification's `addedDate`: [now] when
 * [Notice.addedDateUnknown], else [Notice.addedDate].
 *
 * A notification's `addedDate` is shown as a date by the Notifications screen and the notifications
 * widget, and `NotificationDao` returns notifications ordered by it, descending. Copied as is, the
 * unknown value would show there as the epoch date (1970-01-01, or 1969-12-31 in a zone behind UTC)
 * and be listed after every notification timed after the epoch.
 *
 * [now] is meant to be the current time, which is also the `Notification` entity's default and what
 * `Notifications` gives lesson-change and lucky-number notifications. It is a parameter so that a
 * test can fix it.
 */
internal fun noticeNotificationTime(notice: Notice, now: Long): Long = when {
    notice.addedDateUnknown -> now
    else -> notice.addedDate
}
