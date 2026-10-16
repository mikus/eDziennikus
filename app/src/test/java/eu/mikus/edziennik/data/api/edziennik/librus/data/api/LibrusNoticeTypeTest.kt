/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.mikus.edziennik.data.db.entity.Notice
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LibrusNoticeTypeTest {

    private fun note(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test fun `Positive 0 is a negative notice`() {
        assertEquals(Notice.TYPE_NEGATIVE, librusNoticeType(note("""{"Positive":0}""")))
    }

    @Test fun `Positive 1 is a positive notice`() {
        assertEquals(Notice.TYPE_POSITIVE, librusNoticeType(note("""{"Positive":1}""")))
    }

    @Test fun `Positive 2 is a neutral notice`() {
        assertEquals(Notice.TYPE_NEUTRAL, librusNoticeType(note("""{"Positive":2}""")))
    }

    @Test fun `a quoted Positive still coerces`() {
        assertEquals(Notice.TYPE_POSITIVE, librusNoticeType(note("""{"Positive":"1"}""")))
    }

    @Test fun `a missing Positive is unknown, not neutral`() {
        assertEquals(Notice.TYPE_UNKNOWN, librusNoticeType(note("""{}""")))
    }

    @Test fun `a JSON null Positive is unknown`() {
        assertEquals(Notice.TYPE_UNKNOWN, librusNoticeType(note("""{"Positive":null}""")))
    }

    @Test fun `an undocumented Positive is unknown`() {
        assertEquals(Notice.TYPE_UNKNOWN, librusNoticeType(note("""{"Positive":3}""")))
    }

    @Test fun `an unreadable string Positive is unknown`() {
        assertEquals(Notice.TYPE_UNKNOWN, librusNoticeType(note("""{"Positive":"abc"}""")))
    }

    @Test fun `a boolean Positive is unknown`() {
        assertEquals(Notice.TYPE_UNKNOWN, librusNoticeType(note("""{"Positive":true}""")))
    }

    @Test fun `an object Positive is unknown`() {
        assertEquals(Notice.TYPE_UNKNOWN, librusNoticeType(note("""{"Positive":{}}""")))
    }
}
