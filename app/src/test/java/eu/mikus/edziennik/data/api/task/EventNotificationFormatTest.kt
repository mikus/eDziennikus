/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */

package eu.mikus.edziennik.data.api.task

import eu.mikus.edziennik.R
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class EventNotificationFormatTest {

    @Test fun `homework with a subject and a known date`() {
        assertEquals(
            R.string.notification_homework_format,
            eventNotificationFormat(isHomework = true, hasSubject = true, dateUnknown = false),
        )
    }

    @Test fun `homework without a subject and a known date`() {
        assertEquals(
            R.string.notification_homework_no_subject_format,
            eventNotificationFormat(isHomework = true, hasSubject = false, dateUnknown = false),
        )
    }

    @Test fun `homework with a subject and an unknown date`() {
        assertEquals(
            R.string.notification_homework_unknown_date_format,
            eventNotificationFormat(isHomework = true, hasSubject = true, dateUnknown = true),
        )
    }

    @Test fun `homework without a subject and an unknown date`() {
        assertEquals(
            R.string.notification_homework_unknown_date_no_subject_format,
            eventNotificationFormat(isHomework = true, hasSubject = false, dateUnknown = true),
        )
    }

    @Test fun `event with a subject and a known date`() {
        assertEquals(
            R.string.notification_event_format,
            eventNotificationFormat(isHomework = false, hasSubject = true, dateUnknown = false),
        )
    }

    @Test fun `event without a subject and a known date`() {
        assertEquals(
            R.string.notification_event_no_subject_format,
            eventNotificationFormat(isHomework = false, hasSubject = false, dateUnknown = false),
        )
    }

    @Test fun `event with a subject and an unknown date`() {
        assertEquals(
            R.string.notification_event_unknown_date_format,
            eventNotificationFormat(isHomework = false, hasSubject = true, dateUnknown = true),
        )
    }

    @Test fun `event without a subject and an unknown date`() {
        assertEquals(
            R.string.notification_event_unknown_date_no_subject_format,
            eventNotificationFormat(isHomework = false, hasSubject = false, dateUnknown = true),
        )
    }
}
