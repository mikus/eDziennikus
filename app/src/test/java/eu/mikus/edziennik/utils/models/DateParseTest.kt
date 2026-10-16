/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */
package eu.mikus.edziennik.utils.models

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateParseTest {

    /** Inputs today's `fromY_m_d` reads, and the Date each must yield. */
    private val readable = listOf(
        "2026-03-04" to Date(2026, 3, 4),
        "2026-03-04T10:20:30" to Date(2026, 3, 4), // a trailing time is outside substring(0, 10)
        "2026-03-04 10:20:30" to Date(2026, 3, 4),
        "2026/03/04" to Date(2026, 3, 4),          // positions are read, separators are not checked
        "0001-01-01" to Date(1, 1, 1),
        "2026-13-45" to Date(2026, 13, 45),        // no calendar validation, by design
    )

    /** Inputs today's `fromY_m_d` cannot read. */
    private val unreadable = listOf(
        null,
        "",
        "2026-03",      // ends before the day
        "2026-03-0",    // one character short of the day
        "abcd-ef-gh",
        " 2026-03-04",  // a leading space shifts every field
        "04.03.2026",
    )

    @Test
    fun `fromY_m_dOrNull parses what fromY_m_d parses`() {
        readable.forEach { (input, expected) ->
            assertEquals(expected, Date.fromY_m_dOrNull(input), "input: $input")
        }
    }

    @Test
    fun `fromY_m_dOrNull returns null for an unreadable date`() {
        unreadable.forEach { input ->
            assertNull(Date.fromY_m_dOrNull(input), "input: $input")
        }
    }

    @Test
    fun `fromY_m_d still substitutes 2019-01-01 for an unreadable date`() {
        unreadable.forEach { input ->
            assertEquals(Date(2019, 1, 1), Date.fromY_m_d(input), "input: $input")
        }
    }

    @Test
    fun `fromY_m_dOrNull and fromY_m_d agree on every readable input`() {
        readable.forEach { (input, _) ->
            assertEquals(Date.fromY_m_d(input), Date.fromY_m_dOrNull(input), "input: $input")
        }
    }
}
