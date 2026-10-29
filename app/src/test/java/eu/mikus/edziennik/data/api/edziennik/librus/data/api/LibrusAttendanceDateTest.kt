/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.utils.models.Date
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LibrusAttendanceDateTest {

    @Test fun `a readable date is not unknown`() {
        assertEquals(
            LibrusAttendanceDate(Date(2026, 3, 10), false),
            librusAttendanceDate("2026-03-10"),
        )
    }

    /**
     * 2019-01-01 is also the date an unknown one is filed at, so a school that really says
     * 2019-01-01 is told apart from one that said nothing by the flag, never by the value.
     */
    @Test fun `a real 2019-01-01 is a date, not unknown`() {
        assertEquals(
            LibrusAttendanceDate(Date(2019, 1, 1), false),
            librusAttendanceDate("2019-01-01"),
        )
    }

    @Test fun `an absent date is unknown and filed at 2019-01-01`() {
        assertEquals(
            LibrusAttendanceDate(Date(2019, 1, 1), true),
            librusAttendanceDate(null),
        )
    }

    @Test fun `an empty date is unknown`() {
        assertEquals(
            LibrusAttendanceDate(Date(2019, 1, 1), true),
            librusAttendanceDate(""),
        )
    }

    @Test fun `a truncated date is unknown`() {
        assertEquals(
            LibrusAttendanceDate(Date(2019, 1, 1), true),
            librusAttendanceDate("2026-03"),
        )
    }

    @Test fun `a non-numeric date is unknown`() {
        assertEquals(
            LibrusAttendanceDate(Date(2019, 1, 1), true),
            librusAttendanceDate("not-a-date"),
        )
    }

    /**
     * Readable is not valid: `Date.fromY_m_dOrNull` reads fixed positions and checks neither the
     * separators nor the ranges, so a digit-shaped non-date is read as a date and is not flagged.
     * This pins that limit so it is not mistaken for a bug in the reader.
     */
    @Test fun `a digit-shaped non-date parses as a date and is not flagged`() {
        assertEquals(
            LibrusAttendanceDate(Date(2019, 99, 99), false),
            librusAttendanceDate("2019-99-99"),
        )
    }
}
