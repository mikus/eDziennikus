/*
 * Copyright (c) Mikolaj Olszewski 2026-11-6.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LibrusEventTeamTest {

    private fun event(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test fun `a whole-class event's Class is its team`() {
        assertEquals(69883L, librusEventTeamId(event("""{"Class":{"Id":69883}}""")))
    }

    /**
     * The defect item 82 is about. A captured `HomeWorks` body carries `VirtualClass` for a group's
     * event and no `Class` at all, so reading `Class` alone stored `-1` and the event could not be
     * told from one the school named no team for.
     */
    @Test fun `a group event's VirtualClass is its team`() {
        assertEquals(1378647L, librusEventTeamId(event("""{"VirtualClass":{"Id":1378647}}""")))
    }

    /**
     * Not a shape the captured bodies produce - 39 events and 36 lesson entries carried one key or
     * the other and never both - but it pins the order `LibrusApiTimetables` reads the same pair
     * in, so the event side cannot drift from the lesson side if Librus ever sends both.
     */
    @Test fun `both keys present prefers Class`() {
        assertEquals(
            69883L,
            librusEventTeamId(event("""{"Class":{"Id":69883},"VirtualClass":{"Id":1378647}}""")),
        )
    }

    @Test fun `an event naming no team is -1`() {
        assertEquals(-1L, librusEventTeamId(event("""{"Content":"x"}""")))
    }

    @Test fun `a Class without an Id is -1`() {
        assertEquals(-1L, librusEventTeamId(event("""{"Class":{"Url":"https://api.librus.pl/"}}""")))
    }

    @Test fun `a Class that is not an object is -1`() {
        assertEquals(-1L, librusEventTeamId(event("""{"Class":69883}""")))
        assertEquals(-1L, librusEventTeamId(event("""{"Class":null}""")))
    }
}
