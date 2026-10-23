/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class LibrusEventDateTest {

    private val today = Date(2026, 6, 15)

    @Test fun `a readable date is used and is not unknown`() {
        val result = librusEventDate("2026-09-30", today)
        assertEquals(Date(2026, 9, 30), result.date)
        assertFalse(result.unknown)
    }

    /**
     * 2019-01-01 is what `Date.fromY_m_d` returns for an unreadable argument, so a school that
     * really says 2019-01-01 must not be mistaken for one that said nothing.
     */
    @Test fun `a real 2019-01-01 is not unknown`() {
        val result = librusEventDate("2019-01-01", today)
        assertEquals(Date(2019, 1, 1), result.date)
        assertFalse(result.unknown)
    }

    @Test fun `a null date falls back to today and is unknown`() {
        val result = librusEventDate(null, today)
        assertEquals(today, result.date)
        assertTrue(result.unknown)
    }

    @Test fun `a malformed date falls back to today`() {
        val result = librusEventDate("not-a-date", today)
        assertEquals(today, result.date)
        assertTrue(result.unknown)
    }

    @Test fun `a truncated date falls back to today`() {
        val result = librusEventDate("2026-09", today)
        assertEquals(today, result.date)
        assertTrue(result.unknown)
    }

    @Test fun `an empty date falls back to today`() {
        val result = librusEventDate("", today)
        assertEquals(today, result.date)
        assertTrue(result.unknown)
    }

    /**
     * LibrusSynergiaHomework computes `seen = eventDate < today`, so a fallback in the past would
     * mark the homework seen AND notified — no notification, no unread badge.
     * HomeworkViewModel.classify keeps a homework in the Current tab only while
     * `event.date >= todayDate`, so a past fallback would hide it there too.
     */
    @Test fun `the invented date is never in the past`() {
        listOf(null, "", "2026-09", "not-a-date").forEach { raw ->
            val result = librusEventDate(raw, today)
            assertFalse(result.date < today, "raw=$raw was filed at ${result.date}, before $today")
            assertTrue(result.unknown, "raw=$raw")
        }
    }

    private fun event(eventDate: LibrusEventDate, time: Time? = null, color: Int? = null) =
        librusEvent(
            profileId = 1,
            id = 42L,
            eventDate = eventDate,
            time = time,
            topic = "zadanie z polskiego",
            color = color,
            type = Event.TYPE_HOMEWORK,
            teacherId = 7L,
            subjectId = 8L,
            teamId = 9L,
            addedDate = 1_000L,
        )

    @Test fun `the unknown flag is carried onto the event`() {
        val built = event(LibrusEventDate(today, unknown = true))
        assertTrue(built.dateUnknown)
        assertEquals(today, built.date)
    }

    @Test fun `a readable date is carried as not unknown`() {
        // Review Focus 1. Cannot fail while Event.dateUnknown initialises to false - it documents
        // the contract, it does not pin it. Task 3 is what pins the clearing half.
        val known = Date(2026, 9, 30)
        val built = event(LibrusEventDate(known, unknown = false))
        assertFalse(built.dateUnknown)
        assertEquals(known, built.date)
    }

    @Test fun `the parsed pair reaches the event unchanged`() {
        val built = event(librusEventDate("not-a-date", today))
        assertTrue(built.dateUnknown)
        assertEquals(today, built.date)
    }

    @Test fun `the nullable fields are not transposed`() {
        // Review Focus 2: time and color are the only two nullable parameters and sit next to
        // neither each other nor their own types' neighbours.
        val built = event(LibrusEventDate(today, unknown = false), time = Time(9, 5, 0), color = 0x112233)
        assertEquals(Time(9, 5, 0), built.time)
        assertEquals(0x112233, built.color)
        assertEquals("zadanie z polskiego", built.topic)
        assertEquals(Event.TYPE_HOMEWORK, built.type)
    }
}
