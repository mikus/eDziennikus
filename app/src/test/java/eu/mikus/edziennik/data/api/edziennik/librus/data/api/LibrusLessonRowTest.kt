/*
 * Copyright (c) Mikolaj Olszewski 2026-11-6.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LibrusLessonRowTest {

    private fun lesson(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private val whole =
        """{"Id":3788509,"Teacher":{"Id":1900392},"Subject":{"Id":101006},"Class":{"Id":69883}}"""

    @Test fun `a whole-class assignment keeps every field`() {
        val row = librusLessonRow(3, lesson(whole))
        assertEquals(3, row?.profileId)
        assertEquals(3788509L, row?.lessonId)
        assertEquals(1900392L, row?.teacherId)
        assertEquals(101006L, row?.subjectId)
        assertEquals(69883L, row?.teamId)
    }

    /**
     * The group form of the same assignment, and the defect. These are the real ids of two
     * `/2.0/Lessons` entries for one subject on one profile: one taught to the whole class, one to
     * a group. Reading `Class` alone stored null for the second, so `LibrusApiAttendances`' lookup
     * could say which teacher and subject a mark belonged to but never which group.
     */
    @Test fun `a group assignment's VirtualClass is its team`() {
        val row = librusLessonRow(
            3,
            lesson("""{"Id":3523618,"Teacher":{"Id":1900392},"Subject":{"Id":101006},"VirtualClass":{"Id":1378647}}"""),
        )
        assertEquals(3523618L, row?.lessonId)
        assertEquals(1378647L, row?.teamId)
    }

    @Test fun `an assignment with no id is dropped`() {
        assertNull(librusLessonRow(3, lesson("""{"Teacher":{"Id":1},"Subject":{"Id":2},"Class":{"Id":3}}""")))
    }

    @Test fun `an assignment with no teacher is dropped`() {
        assertNull(librusLessonRow(3, lesson("""{"Id":1,"Subject":{"Id":2},"Class":{"Id":3}}""")))
    }

    @Test fun `an assignment with no subject is dropped`() {
        assertNull(librusLessonRow(3, lesson("""{"Id":1,"Teacher":{"Id":2},"Class":{"Id":3}}""")))
    }

    /**
     * Unlike the other three, a missing team does not drop the row: `teamId` is nullable and the
     * row is still the teacher/subject lookup `LibrusApiAttendances` wants.
     */
    @Test fun `an assignment naming no team is kept with a null team`() {
        val row = librusLessonRow(3, lesson("""{"Id":1,"Teacher":{"Id":2},"Subject":{"Id":3}}"""))
        assertEquals(1L, row?.lessonId)
        assertNull(row?.teamId)
    }
}
