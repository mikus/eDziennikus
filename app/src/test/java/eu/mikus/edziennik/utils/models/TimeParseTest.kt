/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */
package eu.mikus.edziennik.utils.models

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeParseTest {

    /** Inputs today's `fromH_m` reads, and the Time each must yield. */
    private val readable = listOf(
        "08:30" to Time(8, 30, 0),
        "08:30:45" to Time(8, 30, 0), // seconds are outside substring(0, 5)
        "08-30" to Time(8, 30, 0),    // positions are read, separators are not checked
        "00:00" to Time(0, 0, 0),
        "25:99" to Time(25, 99, 0),   // no clock validation, by design
    )

    /** Inputs today's `fromH_m` cannot read. */
    private val unreadable = listOf(
        null,
        "",
        "0830",   // ends before the minute
        "8:00",   // a single-digit hour puts ':' inside substring(0, 2)
        "ab:cd",
        " 8:30",
    )

    @Test
    fun `fromH_mOrNull parses what fromH_m parses`() {
        readable.forEach { (input, expected) ->
            assertEquals(expected, Time.fromH_mOrNull(input), "input: $input")
        }
    }

    @Test
    fun `fromH_mOrNull returns null for an unreadable time`() {
        unreadable.forEach { input ->
            assertNull(Time.fromH_mOrNull(input), "input: $input")
        }
    }

    @Test
    fun `fromH_m still substitutes midnight for an unreadable time`() {
        unreadable.forEach { input ->
            assertEquals(Time(0, 0, 0), Time.fromH_m(input), "input: $input")
        }
    }

    @Test
    fun `fromH_mOrNull and fromH_m agree on every readable input`() {
        readable.forEach { (input, _) ->
            assertEquals(Time.fromH_m(input), Time.fromH_mOrNull(input), "input: $input")
        }
    }
}
