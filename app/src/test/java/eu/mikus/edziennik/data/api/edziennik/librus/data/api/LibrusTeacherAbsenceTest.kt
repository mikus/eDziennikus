/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Every input is the readable record below, edited only in the fields under test. Each skip test
 * edits exactly one field, so its record differs from a kept one in that field alone. The date
 * tests leave the other date readable: a half-readable record is skipped whole, never filed with
 * one date.
 */
class LibrusTeacherAbsenceTest {

    private fun absence(edit: JsonObject.() -> Unit = {}): JsonObject = JsonParser.parseString(
        """
        {"Id":5,"Teacher":{"Id":7},"DateFrom":"2026-11-05","DateTo":"2026-11-07",
         "TimeFrom":"08:00:00","TimeTo":"16:00:00"}
        """
    ).asJsonObject.apply(edit)

    @Test fun `a readable absence maps every field`() {
        val result = assertNotNull(librusTeacherAbsence(1, absence()))
        assertEquals(1, result.profileId)
        assertEquals(5L, result.id)
        assertEquals(-1L, result.type)
        assertNull(result.name)
        assertEquals(Date(2026, 11, 5), result.dateFrom)
        assertEquals(Date(2026, 11, 7), result.dateTo)
        assertEquals(Time(8, 0, 0), result.timeFrom)
        assertEquals(Time(16, 0, 0), result.timeTo)
        assertEquals(7L, result.teacherId)
    }

    @Test fun `absent times stay null`() {
        val result = assertNotNull(librusTeacherAbsence(1, absence {
            remove("TimeFrom")
            remove("TimeTo")
        }))
        assertNull(result.timeFrom)
        assertNull(result.timeTo)
    }

    @Test fun `a missing Id skips the record`() {
        assertNull(librusTeacherAbsence(1, absence { remove("Id") }))
    }

    @Test fun `a missing teacher skips the record`() {
        assertNull(librusTeacherAbsence(1, absence { remove("Teacher") }))
    }

    /**
     * 2019-01-01 is what `Date.fromY_m_d` answers for an unreadable date, but a school that really
     * sends it still gets an absence on that day: the skip keys on the read failing, not the value.
     */
    @Test fun `a real 2019-01-01 is kept`() {
        val result = assertNotNull(librusTeacherAbsence(1, absence {
            addProperty("DateFrom", "2019-01-01")
            addProperty("DateTo", "2019-01-01")
        }))
        assertEquals(Date(2019, 1, 1), result.dateFrom)
        assertEquals(Date(2019, 1, 1), result.dateTo)
    }

    @Test fun `an absent DateFrom skips the record`() {
        assertNull(librusTeacherAbsence(1, absence { remove("DateFrom") }))
    }

    @Test fun `a malformed DateFrom skips the record`() {
        assertNull(librusTeacherAbsence(1, absence { addProperty("DateFrom", "garbage") }))
    }

    @Test fun `an absent DateTo skips the record`() {
        assertNull(librusTeacherAbsence(1, absence { remove("DateTo") }))
    }

    @Test fun `a malformed DateTo skips the record`() {
        assertNull(librusTeacherAbsence(1, absence { addProperty("DateTo", "2026-11") }))
    }
}
