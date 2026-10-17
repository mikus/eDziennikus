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

    // Neither its year start nor its year end is what the fallback would give (1 September,
    // 30 June), so an accessor that ignores a stored value of a consistent profile cannot pass by
    // coincidence. Polish school years do start on 2 September.
    private val healthy = profile(Date(y, 9, 2), Date(y + 1, 1, 15), Date(y + 1, 6, 21))

    // every key present but unreadable, the shape that collapsed onto 2019-01-01 together
    private val unreadable = profile(Date(2019, 1, 1), Date(2019, 1, 1), Date(2019, 1, 1))

    // both semester starts readable, only the year end not: EndSchoolYear is written last, so the
    // first two assignments had already succeeded. 15 January is not the 1 February fallback.
    private val endUnreadable = profile(Date(y, 9, 2), Date(y + 1, 1, 15), Date(2019, 1, 1))

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
        assertFalse(damaged.hasConsistentSchoolYear())
    }

    @Test
    fun `three identical dates are not a school year`() {
        assertFalse(unreadable.hasConsistentSchoolYear())
    }

    @Test
    fun `a year that ends before semester 2 starts is not a school year`() {
        assertFalse(profile(Date(y, 9, 1), Date(y + 1, 2, 1), Date(y + 1, 1, 31)).hasConsistentSchoolYear())
    }

    @Test
    fun `a year that ends the day semester 2 starts is still a school year`() {
        // the second comparison is non-strict on purpose
        assertTrue(profile(Date(y, 9, 1), Date(y + 1, 6, 30), Date(y + 1, 6, 30)).hasConsistentSchoolYear())
    }

    @Test
    fun `a stored school year is honoured`() {
        assertEquals(Date(y, 9, 2), healthy.getSemesterStart(1))
        assertEquals(Date(y + 1, 1, 15), healthy.getSemesterStart(2))
        assertEquals(Date(y + 1, 6, 21), healthy.getSemesterEnd(2))
    }

    @Test
    fun `semester starts fall back instead of reporting 2019`() {
        assertEquals(Date(y, 9, 1), damaged.getSemesterStart(1))
        assertEquals(fallbackSem2, damaged.getSemesterStart(2))
        assertEquals(Date(y + 1, 6, 30), damaged.getSemesterEnd(2))
        // every key unreadable falls back too, rather than reporting 2019
        assertEquals(Date(y, 9, 1), unreadable.getSemesterStart(1))
        assertEquals(fallbackSem2, unreadable.getSemesterStart(2))
        assertEquals(Date(y + 1, 6, 30), unreadable.getSemesterEnd(2))
    }

    @Test
    fun `a readable semester boundary survives an unreadable year end`() {
        // the boundary depends on the two semester starts alone, so a year end that cannot be
        // read must not throw away a boundary the school did send
        assertEquals(Date(y, 9, 2), endUnreadable.getSemesterStart(1))
        assertEquals(Date(y + 1, 1, 15), endUnreadable.getSemesterStart(2))
        assertEquals(Date(y + 1, 1, 14), endUnreadable.getSemesterEnd(1))
        assertEquals(1, endUnreadable.dateToSemester(Date(y + 1, 1, 14)))
        assertEquals(2, endUnreadable.dateToSemester(Date(y + 1, 1, 20)))
        // the year end itself is not readable, so it alone falls back
        assertEquals(Date(y + 1, 6, 30), endUnreadable.getSemesterEnd(2))
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
