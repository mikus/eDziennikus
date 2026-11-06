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
