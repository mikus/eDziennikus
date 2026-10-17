/*
 * Copyright (c) Mikolaj Olszewski 2026-10-17.
 */
package eu.mikus.edziennik.ext

import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.utils.models.Date
import org.junit.jupiter.api.Test
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
}
