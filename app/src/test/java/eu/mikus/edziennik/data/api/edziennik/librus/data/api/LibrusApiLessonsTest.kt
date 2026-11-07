/*
 * Copyright (c) Mikolaj Olszewski 2026-11-7.
 */
package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_LESSONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real `LibrusApiLessons` driven over a canned `/Lessons` body - see [CannedLibrusApi].
 *
 * `LibrusLessonRowTest` already pins [librusLessonRow] itself. What it cannot pin is that the parser
 * *calls* it: restore the inline, `Class`-only read the parser used before item 82 and every test
 * that existed before this one stays green, with the defect back - measured, 1238 of 1238. The
 * `VirtualClass` case below is the assertion that goes red for it, so it is load-bearing and not an
 * extra shape for symmetry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrusApiLessonsTest {

    /**
     * One assignment of each shape the endpoint produces: three usable and **three** unusable - one
     * missing its `Teacher`, one its `Subject`, one its own `Id`, matching [librusLessonRow]'s three
     * `?: return null` guards. They sit in the same body as the usable ones so a parser that stopped
     * dropping them is caught by the row count. The missing-`Id` entry is pinned by that count alone
     * (it has no id to assert `null` on), so do not raise the expected 3 without replacing it.
     */
    private fun lessons() = CannedLibrusApi().apply {
        serve("Lessons", """
            {"Lessons": [
              {"Id": 1, "Teacher": {"Id": 10}, "Subject": {"Id": 20}, "Class": {"Id": 30}},
              {"Id": 2, "Teacher": {"Id": 11}, "Subject": {"Id": 21}, "VirtualClass": {"Id": 31}},
              {"Id": 3, "Teacher": {"Id": 12}, "Subject": {"Id": 22}},
              {"Id": 4, "Subject": {"Id": 23}, "Class": {"Id": 30}},
              {"Id": 5, "Teacher": {"Id": 13}, "Class": {"Id": 30}},
              {"Teacher": {"Id": 14}, "Subject": {"Id": 24}, "Class": {"Id": 30}}
            ]}
        """.trimIndent())
    }

    @Test fun `a group assignment is stored under its VirtualClass`() {
        val api = lessons()

        api.drive { data, onSuccess -> LibrusApiLessons(data, null, onSuccess) }

        assertEquals(
            "a Lessons entry naming only a VirtualClass must be stored under that group, not null",
            31L,
            api.data.librusLessons.get(2L).teamId,
        )
    }

    @Test fun `a whole-class assignment is stored under its Class`() {
        val api = lessons()

        api.drive { data, onSuccess -> LibrusApiLessons(data, null, onSuccess) }

        assertEquals(30L, api.data.librusLessons.get(1L).teamId)
    }

    @Test fun `an assignment naming no team is kept, without one`() {
        val api = lessons()

        api.drive { data, onSuccess -> LibrusApiLessons(data, null, onSuccess) }

        assertNull(
            "teamId is nullable and the row is still a usable teacher/subject lookup without it",
            api.data.librusLessons.get(3L).teamId,
        )
    }

    @Test fun `an assignment missing its id, teacher or subject is dropped`() {
        val api = lessons()

        api.drive { data, onSuccess -> LibrusApiLessons(data, null, onSuccess) }

        assertNull("no Teacher: the lookup would answer nothing", api.data.librusLessons.get(4L))
        assertNull("no Subject: likewise", api.data.librusLessons.get(5L))
        assertEquals("and the three usable entries are all that is stored", 3, api.data.librusLessons.size())
    }

    @Test fun `the teacher and subject of a stored assignment are read`() {
        val api = lessons()

        api.drive { data, onSuccess -> LibrusApiLessons(data, null, onSuccess) }

        val row = api.data.librusLessons.get(1L)
        assertEquals(10L, row.teacherId)
        assertEquals(20L, row.subjectId)
        assertEquals("the null profile makes this -1", -1, row.profileId)
    }

    @Test fun `the endpoint reports itself done and records a timer`() {
        val api = lessons()

        val reported = api.drive { data, onSuccess -> LibrusApiLessons(data, null, onSuccess) }

        assertEquals(ENDPOINT_LIBRUS_API_LESSONS, reported)
        assertEquals(
            "exactly one timer, for this endpoint",
            listOf(ENDPOINT_LIBRUS_API_LESSONS),
            api.data.endpointTimers.map { it.endpointId },
        )
    }
}
