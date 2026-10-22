/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */

package eu.mikus.edziennik.data.api.task

import androidx.annotation.StringRes
import eu.mikus.edziennik.R

/**
 * Picks the short-text format of an event or homework notification.
 *
 * Eight formats, one per combination of the three flags. The `dateUnknown` ones take no date
 * argument at all: the date phrase would land in a case-governed slot (`na %2$s`, `dnia %2$s`) and
 * "data nieznana" is nominative, so those formats put the unknown date after an em dash instead
 * ("… — termin nieznany"). Their placeholder indices are contiguous, so no placeholder reads a
 * date and the caller must not pass one.
 *
 * Argument order each format takes, which the caller in `Notifications.eventNotifications()` must
 * match:
 *  - homework: subject, date
 *  - homework, no subject: (Polish reads the date as `%2$s`, so the caller still passes the pair)
 *  - event: type, date, subject
 *  - event, no subject: type, date
 *  - homework, date unknown: subject
 *  - homework, date unknown, no subject: none
 *  - event, date unknown: type, subject
 *  - event, date unknown, no subject: type
 */
@StringRes
internal fun eventNotificationFormat(
    isHomework: Boolean,
    hasSubject: Boolean,
    dateUnknown: Boolean,
): Int = when {
    isHomework && dateUnknown && hasSubject -> R.string.notification_homework_unknown_date_format
    isHomework && dateUnknown -> R.string.notification_homework_unknown_date_no_subject_format
    isHomework && hasSubject -> R.string.notification_homework_format
    isHomework -> R.string.notification_homework_no_subject_format
    dateUnknown && hasSubject -> R.string.notification_event_unknown_date_format
    dateUnknown -> R.string.notification_event_unknown_date_no_subject_format
    hasSubject -> R.string.notification_event_format
    else -> R.string.notification_event_no_subject_format
}
