/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.utils.models.Date
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LibrusNoticeDateTest {

    private fun profile(sem1: Date, sem2: Date) =
        Profile(1, 1, LoginType.LIBRUS).apply {
            dateSemester1Start = sem1
            dateSemester2Start = sem2
        }

    // Both starts are stored, so a readable date's semester does not depend on the run date: a
    // default profile's starts are derived from the current school year.
    private val profile = profile(Date(2025, 9, 1), Date(2026, 2, 1))

    @Test fun `a readable date in semester 1`() {
        assertEquals(
            LibrusNoticeDate(Date(2025, 11, 3).inMillis, 1),
            librusNoticeDate("2025-11-03", profile),
        )
    }

    @Test fun `a readable date in semester 2`() {
        assertEquals(
            LibrusNoticeDate(Date(2026, 3, 10).inMillis, 2),
            librusNoticeDate("2026-03-10", profile),
        )
    }

    /**
     * 2019-01-01 is what `Date.fromY_m_d` returns for an unreadable argument, so a school that
     * really says 2019-01-01 must not be mistaken for one that said nothing.
     */
    @Test fun `a real 2019-01-01 is a date, not unknown`() {
        assertEquals(Date(2019, 1, 1).inMillis, librusNoticeDate("2019-01-01", profile).addedDate)
    }

    @Test fun `an absent date is unknown`() {
        assertEquals(Notice.ADDED_DATE_UNKNOWN, librusNoticeDate(null, profile).addedDate)
    }

    @Test fun `an empty date is unknown`() {
        assertEquals(Notice.ADDED_DATE_UNKNOWN, librusNoticeDate("", profile).addedDate)
    }

    @Test fun `a truncated date is unknown`() {
        assertEquals(Notice.ADDED_DATE_UNKNOWN, librusNoticeDate("2025-11", profile).addedDate)
    }

    @Test fun `a malformed date is unknown`() {
        assertEquals(Notice.ADDED_DATE_UNKNOWN, librusNoticeDate("garbage", profile).addedDate)
    }

    /**
     * `Profile.currentSemester` reads the wall clock, so neither profile's answer may depend on the
     * run date. A's semester 2 started on 2020-02-01, so it is semester 2 from then on; B's
     * semester 1 starts on 2998-09-01, so it is semester 1 until 2999-02-01. Each stores its two
     * starts in order, so `getSemesterStart` returns them and never falls back on the current
     * school year.
     *
     * A is checked first and is the one that tells today's semester from a parsed date's: its
     * semester 2 starts after 2019-01-01, so a writer that files an unreadable date at 2019-01-01
     * and takes that date's semester answers 1 here, not 2.
     */
    @Test fun `an unknown date takes the profile's current semester`() {
        val a = profile(Date(2019, 9, 1), Date(2020, 2, 1))
        val b = profile(Date(2998, 9, 1), Date(2999, 2, 1))
        assertEquals(2, librusNoticeDate(null, a).semester)
        assertEquals(1, librusNoticeDate(null, b).semester)
    }

    @Test fun `no profile means semester 1`() {
        // 2026-03-10 is semester 2 under `profile`, so a 1 here comes from the missing profile
        assertEquals(1, librusNoticeDate("2026-03-10", null).semester)
        assertEquals(1, librusNoticeDate(null, null).semester)
    }
}
