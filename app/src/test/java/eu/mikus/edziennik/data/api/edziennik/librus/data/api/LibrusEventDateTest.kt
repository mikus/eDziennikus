/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.utils.models.Date
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
     * HomeworkViewModel:70 reads `date >= today` for the Current tab and would hide it there too.
     */
    @Test fun `the invented date is never in the past`() {
        listOf(null, "", "2026-09", "not-a-date").forEach { raw ->
            val result = librusEventDate(raw, today)
            assertFalse(result.date < today, "raw=$raw was filed at ${result.date}, before $today")
            assertTrue(result.unknown, "raw=$raw")
        }
    }
}
