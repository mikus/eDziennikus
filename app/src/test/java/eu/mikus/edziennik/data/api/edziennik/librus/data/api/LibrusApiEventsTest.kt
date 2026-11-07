/*
 * Copyright (c) Mikolaj Olszewski 2026-11-7.
 */
package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_EVENTS
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real `LibrusApiEvents` driven over a canned `/HomeWorks` body - see [CannedLibrusApi].
 *
 * `LibrusTeamIdTest` pins [librusEventTeamId] itself; nothing pinned that the parser calls it.
 * Reading `Class` alone, as the parser did before item 82, stored `-1` for every group event - the
 * same value as "the school named no team" - so the timetable could not tell a group's event from an
 * unattributed one. The three events below are the three outcomes that read has to tell apart, and
 * the first of them is what goes red for that revert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrusApiEventsTest {

    private fun events() = CannedLibrusApi().apply {
        serve("HomeWorks", """
            {"HomeWorks": [
              {"Id": 7, "Date": "2026-05-04", "Content": "group", "Category": {"Id": 5},
               "CreatedBy": {"Id": 6}, "Subject": {"Id": 8}, "VirtualClass": {"Id": 41},
               "LessonNo": 3, "TimeFrom": "08:00:00", "AddDate": "2026-05-01 10:00:00"},
              {"Id": 8, "Date": "2026-05-05", "Content": "whole class", "Category": {"Id": 5},
               "CreatedBy": {"Id": 6}, "Subject": {"Id": 8}, "Class": {"Id": 40},
               "LessonNo": 4, "TimeFrom": "08:55:00", "AddDate": "2026-05-01 10:00:00"},
              {"Id": 9, "Date": "2026-05-06", "Content": "no team", "Category": {"Id": 5},
               "CreatedBy": {"Id": 6}, "Subject": {"Id": 8},
               "LessonNo": 5, "TimeFrom": "09:50:00", "AddDate": "2026-05-01 10:00:00"}
            ]}
        """.trimIndent())
    }

    private fun teamIdOf(api: CannedLibrusApi, id: Long) =
        api.data.eventList.single { it.id == id }.teamId

    @Test fun `a group event is stored under its VirtualClass`() {
        val api = events()

        api.drive { data, onSuccess -> LibrusApiEvents(data, null, onSuccess) }

        assertEquals(
            "an event naming only a VirtualClass must be stored under that group, not under -1",
            41L,
            teamIdOf(api, 7L),
        )
    }

    @Test fun `a whole-class event is stored under its Class`() {
        val api = events()

        api.drive { data, onSuccess -> LibrusApiEvents(data, null, onSuccess) }

        assertEquals(40L, teamIdOf(api, 8L))
    }

    @Test fun `an event naming no team is stored under -1`() {
        val api = events()

        api.drive { data, onSuccess -> LibrusApiEvents(data, null, onSuccess) }

        assertEquals(
            "-1 is the no-team value Event.teamId uses, and it must stay distinct from a group id",
            -1L,
            teamIdOf(api, 9L),
        )
    }

    @Test fun `every event in the body is stored`() {
        val api = events()

        api.drive { data, onSuccess -> LibrusApiEvents(data, null, onSuccess) }

        assertEquals(listOf(7L, 8L, 9L), api.data.eventList.map { it.id })
    }

    @Test fun `the endpoint reports itself done`() {
        val api = events()

        val reported = api.drive { data, onSuccess -> LibrusApiEvents(data, null, onSuccess) }

        assertEquals(ENDPOINT_LIBRUS_API_EVENTS, reported)
    }
}
