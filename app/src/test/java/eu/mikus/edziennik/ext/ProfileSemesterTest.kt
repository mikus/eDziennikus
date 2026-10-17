/*
 * Copyright (c) Mikolaj Olszewski 2026-10-17.
 */
package eu.mikus.edziennik.ext

import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.utils.models.Date
import org.junit.jupiter.api.Test
import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileSemesterTest {

    private fun profile(sem1: Date, sem2: Date, yearEnd: Date) =
        Profile(1, 1, LoginType.LIBRUS).apply {
            dateSemester1Start = sem1
            dateSemester2Start = sem2
            dateYearEnd = yearEnd
        }

    private val y = currentSchoolYearStart()

    // declared below `y`: Kotlin initialises top-down, so a forward reference would read 0
    private val damaged = profile(Date(y, 9, 1), Date(2019, 1, 1), Date(y + 1, 6, 30))
    private val fallbackSem2 = Date(y + 1, 2, 1)

    // its year end is not the 30 June the fallback would give, so an accessor that ignores the
    // stored year end of a consistent profile cannot pass by coincidence
    private val healthy = profile(Date(y, 9, 1), Date(y + 1, 1, 15), Date(y + 1, 6, 21))

    @Test
    fun `a default-constructed profile has a consistent school year`() {
        assertTrue(Profile(1, 1, LoginType.LIBRUS).hasConsistentSchoolYear())
    }

    @Test
    fun `an unusual but coherent school year is consistent`() {
        // a mid-January boundary is real, and a stale-but-coherent trio is an archived profile
        assertTrue(profile(Date(y, 9, 1), Date(y + 1, 1, 15), Date(y + 1, 6, 30)).hasConsistentSchoolYear())
        assertTrue(profile(Date(2024, 9, 1), Date(2025, 2, 1), Date(2025, 6, 30)).hasConsistentSchoolYear())
    }

    @Test
    fun `a boundary before the year start is not a school year`() {
        assertFalse(profile(Date(y, 9, 1), Date(2019, 1, 1), Date(y + 1, 6, 30)).hasConsistentSchoolYear())
    }

    @Test
    fun `three identical dates are not a school year`() {
        assertFalse(profile(Date(2019, 1, 1), Date(2019, 1, 1), Date(2019, 1, 1)).hasConsistentSchoolYear())
    }

    @Test
    fun `a stored school year is honoured`() {
        assertEquals(Date(y, 9, 1), healthy.getSemesterStart(1))
        assertEquals(Date(y + 1, 1, 15), healthy.getSemesterStart(2))
        assertEquals(Date(y + 1, 6, 21), healthy.getSemesterEnd(2))
    }

    @Test
    fun `semester starts fall back instead of reporting 2019`() {
        assertEquals(Date(y, 9, 1), damaged.getSemesterStart(1))
        assertEquals(fallbackSem2, damaged.getSemesterStart(2))
        assertEquals(Date(y + 1, 6, 30), damaged.getSemesterEnd(2))
        // a year end that is itself unreadable falls back too, rather than reporting 2019
        val unreadable = profile(Date(2019, 1, 1), Date(2019, 1, 1), Date(2019, 1, 1))
        assertEquals(Date(y + 1, 6, 30), unreadable.getSemesterEnd(2))
    }

    @Test
    fun `semester 1 ends the day before semester 2 starts`() {
        assertEquals(fallbackSem2.clone().stepForward(0, 0, -1), damaged.getSemesterEnd(1))
        assertEquals(Date(y + 1, 1, 14), healthy.getSemesterEnd(1))
    }

    @Test
    fun `a profile with no school year answers semester like a new one`() {
        // never asserts today's position in the year: both sides move together
        assertEquals(Profile(1, 1, LoginType.LIBRUS).currentSemester, damaged.currentSemester)
        assertEquals(1, damaged.dateToSemester(fallbackSem2.clone().stepForward(0, 0, -1)))
        assertEquals(2, damaged.dateToSemester(fallbackSem2)) // the boundary day is semester 2
    }

    @Test
    fun `the school year starts in September`() {
        // Calendar is the oracle, not Date: every other test reads currentSchoolYearStart() on
        // both sides, so swapping its branches would leave them all green. Calendar.MONTH is
        // 0-based, Date.month is 1-based - Calendar.SEPTEMBER keeps the two from being confused.
        val now = Calendar.getInstance()
        val expected =
            if (now.get(Calendar.MONTH) >= Calendar.SEPTEMBER) now.get(Calendar.YEAR)
            else now.get(Calendar.YEAR) - 1
        assertEquals(expected, currentSchoolYearStart())
    }
}
