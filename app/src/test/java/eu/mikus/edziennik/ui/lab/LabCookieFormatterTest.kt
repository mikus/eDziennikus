/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import okhttp3.Cookie
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabCookieFormatterTest {

    private fun cookie(domain: String, name: String, value: String, persistent: Boolean = false): Cookie =
        Cookie.Builder().domain(domain).name(name).value(value)
            .let { if (persistent) it.expiresAt(4_102_444_800_000L) else it }  // 2100-01-01
            .build()

    @Test
    fun `cookies are grouped by domain, domains sorted, names sorted inside`() {
        val groups = LabCookieFormatter.format(
            listOf(
                cookie("zeta.pl", "b", "2"),
                cookie("alpha.pl", "z", "26"),
                cookie("alpha.pl", "a", "1"),
            ),
        )
        assertEquals(listOf("alpha.pl", "zeta.pl"), groups.map { it.domain })
        assertEquals(listOf("a", "z"), groups.first().lines.map { it.name })
    }

    @Test
    fun `the value is percent-decoded and clipped to forty characters`() {
        // LabPageFragment.kt:196-197 - `.decode().take(40)`.
        val long = "x".repeat(60)
        val line = LabCookieFormatter.format(listOf(cookie("a.pl", "n", "a%20b$long"))).single().lines.single()
        assertTrue(line.value.startsWith("a b"))
        assertEquals(40, line.value.length)
    }

    @Test
    fun `the persistent flag survives, because the readout underlines those names`() {
        // TextExtensions.kt:163 asUnderlineSpannable, sole caller LabPageFragment.kt:191. The cue is
        // kept in Compose as SpanStyle(textDecoration = Underline) - dropping it loses a state cue.
        val groups = LabCookieFormatter.format(
            listOf(cookie("a.pl", "session", "1"), cookie("a.pl", "remember", "2", persistent = true)),
        )
        val lines = groups.single().lines.associateBy { it.name }
        assertTrue(lines.getValue("remember").persistent)
        assertFalse(lines.getValue("session").persistent)
    }

    @Test
    fun `no cookies is an empty list, not an empty group`() {
        assertEquals(emptyList(), LabCookieFormatter.format(emptyList()))
    }
}
