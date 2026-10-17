/*
 * Copyright (c) Mikolaj Olszewski 2026-10-17.
 */
package eu.mikus.edziennik.ext

import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.utils.models.Date
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileSchoolYearTest {

    private fun profile(s1: Date, s2: Date, end: Date, archived: Boolean = false) =
        Profile(1, 1, LoginType.LIBRUS).apply {
            dateSemester1Start = s1
            dateSemester2Start = s2
            dateYearEnd = end
            this.archived = archived
        }

    private val y = currentSchoolYearStart()

    // declared below `y`: Kotlin initialises top-down, so a forward reference would read 0
    private val current = Date(y, 9, 1)..Date(y + 1, 6, 30)

    // what the pre-72 parsers wrote for a key that was present but unreadable
    private val damaged = Date(2019, 1, 1)

    @Test
    fun `a stored school year is honoured`() {
        // Neither bound is what the fallback would give (1 September, 30 June), so a reader that
        // ignores the stored dates cannot pass by coincidence.
        val stored = profile(Date(y, 9, 2), Date(y + 1, 1, 15), Date(y + 1, 6, 21))
        assertEquals(Date(y, 9, 2)..Date(y + 1, 6, 21), stored.getSchoolYear())
    }

    @Test
    fun `an unreadable semester 2 start does not disturb the year`() {
        // the readers never read semester 2, so its damage must not discard the outer bounds
        val stored = profile(Date(y, 9, 2), damaged, Date(y + 1, 6, 21))
        assertEquals(Date(y, 9, 2)..Date(y + 1, 6, 21), stored.getSchoolYear())
    }

    @Test
    fun `a year start years before its end falls back`() {
        assertEquals(current, profile(damaged, Date(y + 1, 1, 29), Date(y + 1, 6, 25)).getSchoolYear())
    }

    @Test
    fun `a year that ends before it starts falls back`() {
        assertEquals(current, profile(Date(y, 9, 1), Date(y + 1, 1, 29), damaged).getSchoolYear())
    }

    @Test
    fun `a one-day school year falls back`() {
        assertEquals(current, profile(damaged, damaged, damaged).getSchoolYear())
    }

    @Test
    fun `a school year running to 31 August is honoured`() {
        // 1 September to 31 August ends 364 days after it starts, the longest legal Polish school
        // year and one day under the anniversary cap. Date compares year*10000+month*100+day and
        // never sees the calendar, so the second, leap-spanning case pins nothing the first does
        // not; it documents that 29 February does not move the cap.
        val plain = profile(Date(2026, 9, 1), Date(2027, 2, 1), Date(2027, 8, 31))
        assertEquals(Date(2026, 9, 1)..Date(2027, 8, 31), plain.getSchoolYear())
        val leap = profile(Date(2027, 9, 1), Date(2028, 2, 1), Date(2028, 8, 31))
        assertEquals(Date(2027, 9, 1)..Date(2028, 8, 31), leap.getSchoolYear())
    }

    @Test
    fun `an archived profile keeps its own coherent year`() {
        val archived = profile(Date(2024, 9, 2), Date(2025, 1, 15), Date(2025, 6, 21), archived = true)
        assertEquals(Date(2024, 9, 2)..Date(2025, 6, 21), archived.getSchoolYear())
    }

    @Test
    fun `a damaged archived profile falls back to its own year`() {
        val archived = profile(damaged, Date(2025, 1, 29), Date(2025, 6, 25), archived = true)
        assertEquals(Date(2024, 9, 1)..Date(2025, 6, 30), archived.getSchoolYear())
    }

    @Test
    fun `a damaged archived profile anchors on its latest stored date`() {
        // the year end is unreadable, so semester 2 is the latest real date. Anchoring on the
        // year end alone would land in 2018/19.
        val archived = profile(Date(2024, 9, 2), Date(2025, 1, 15), damaged, archived = true)
        assertEquals(Date(2024, 9, 1)..Date(2025, 6, 30), archived.getSchoolYear())
    }

    @Test
    fun `every damaged shape yields a range that starts before it ends`() {
        val good = listOf(Date(2024, 9, 2), Date(2025, 1, 15), Date(2025, 6, 21))
        // each non-empty subset of (s1, s2, end) is replaced by 2019-01-01
        for (mask in 1..7) {
            val dates = good.mapIndexed { i, date -> if (mask and (1 shl i) != 0) damaged else date }
            for (archived in listOf(true, false)) {
                val shape = "damaged mask=$mask (s1=${mask and 1 != 0}, s2=${mask and 2 != 0}, " +
                    "end=${mask and 4 != 0}) archived=$archived"
                val range = profile(dates[0], dates[1], dates[2], archived).getSchoolYear()
                assertTrue(range.start < range.endInclusive, "$shape: ${range.start}..${range.endInclusive} does not start before it ends")
                // the calendar library compares YearMonths, so the month must not invert either
                assertTrue(
                    range.start.value / 100 <= range.endInclusive.value / 100,
                    "$shape: ${range.start}..${range.endInclusive} inverts as year-months",
                )
            }
        }
    }

    @Test
    fun `reading the school year never mutates the profile`() {
        // Date.stepForward mutates its receiver and Data.kt persists the profile on every sync, so
        // a bound handed out by reference and walked would be written to the database. The profiles
        // are seeded with their own objects and compared against fresh literals: comparing against
        // the seeds would pass, as a walked field and its seed are the same object.
        val healthy = profile(Date(y, 9, 2), Date(y + 1, 1, 15), Date(y + 1, 6, 21))
        val broken = profile(Date(2019, 1, 1), Date(y + 1, 1, 15), Date(y + 1, 6, 21))
        healthy.getSchoolYear()
        broken.getSchoolYear()
        assertEquals(Date(y, 9, 2), healthy.dateSemester1Start)
        assertEquals(Date(y + 1, 1, 15), healthy.dateSemester2Start)
        assertEquals(Date(y + 1, 6, 21), healthy.dateYearEnd)
        assertEquals(Date(2019, 1, 1), broken.dateSemester1Start)
        assertEquals(Date(y + 1, 1, 15), broken.dateSemester2Start)
        assertEquals(Date(y + 1, 6, 21), broken.dateYearEnd)
    }
}
