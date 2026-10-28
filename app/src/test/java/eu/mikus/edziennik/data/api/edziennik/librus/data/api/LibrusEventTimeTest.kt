/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.db.entity.LessonRange
import eu.mikus.edziennik.utils.models.Time
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LibrusEventTimeTest {

    private val range = LessonRange(1, 3, Time(9, 50, 0), Time(10, 35, 0))

    @Test fun `a lesson range wins over TimeFrom`() {
        assertEquals(Time(9, 50, 0), librusEventTime(range, "12:00:00"))
    }

    @Test fun `a lesson range wins even when TimeFrom is unreadable`() {
        assertEquals(Time(9, 50, 0), librusEventTime(range, "garbage"))
    }

    @Test fun `a readable TimeFrom is used when there is no range`() {
        assertEquals(Time(8, 15, 0), librusEventTime(null, "08:15:00"))
    }

    @Test fun `an absent TimeFrom is no time`() {
        assertNull(librusEventTime(null, null))
    }

    @Test fun `an empty TimeFrom is no time`() {
        assertNull(librusEventTime(null, ""))
    }

    @Test fun `a malformed TimeFrom is no time`() {
        assertNull(librusEventTime(null, "garbage"))
    }

    /**
     * Pins a limit, not a goal. The reader takes the hour from the first two characters, so a
     * single-digit hour is unreadable and the event reads "cały dzień" instead of 8:05 (before,
     * it read "0:00"). Reading it would mean changing the reader; this test makes that a decision.
     */
    @Test fun `a single-digit hour is unreadable`() {
        assertNull(librusEventTime(null, "8:05"))
    }

    /**
     * Pins a limit, not a goal. The reader reads fixed positions and validates neither separators
     * nor ranges, so "25:99" is filed as 25:99 and rendered that way. Rejecting it would mean
     * validating the clock; this test makes that a decision.
     */
    @Test fun `a digit-shaped non-time still parses`() {
        assertEquals(Time(25, 99, 0), librusEventTime(null, "25:99"))
    }

    /**
     * Pins a deliberate non-change, not a goal. PT meetings treat Librus's literal "00:00:00" as
     * "no time", but whether HomeWorks does too is unverified, so here it is still read as a real
     * midnight and renders "0:00". Mapping it to null would be a claim about Librus's data, not a
     * parsing fix; this test makes that claim a decision.
     */
    @Test fun `a literal midnight TimeFrom is kept as midnight`() {
        assertEquals(Time(0, 0, 0), librusEventTime(null, "00:00:00"))
    }
}
