/*
 * Copyright (c) Szkolny.eu 2026-5-12.
 */

package eu.mikus.edziennik.ext

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization tests for [getNameInitials].
 *
 * The first eight cases are lifted verbatim from the function's own KDoc in
 * `TextExtensions.kt` — they are the documented contract. The remaining cases
 * pin down currently-observable behavior that the KDoc does not cover (null
 * receiver, three-word names). If you intentionally change behavior, update the
 * KDoc and the corresponding test in the same commit.
 */
class TextExtensionsTest {

    @Test
    fun `returns two initials for first plus last name`() {
        assertEquals("JS", "John Smith".getNameInitials())
    }

    @Test
    fun `uppercases mixed-case input`() {
        assertEquals("JS", "JOHN SMith".getNameInitials())
    }

    @Test
    fun `returns single initial for single-word input`() {
        assertEquals("J", "John".getNameInitials())
    }

    @Test
    fun `ignores trailing whitespace after single word`() {
        assertEquals("J", "John ".getNameInitials())
    }

    @Test
    fun `collapses runs of whitespace between words`() {
        assertEquals("JS", "John     Smith      ".getNameInitials())
    }

    @Test
    fun `returns empty for single-space input`() {
        assertEquals("", " ".getNameInitials())
    }

    @Test
    fun `returns empty for whitespace-only input`() {
        assertEquals("", "  ".getNameInitials())
    }

    @Test
    fun `returns empty for null receiver`() {
        val name: String? = null
        assertEquals("", name.getNameInitials())
    }

    @Test
    fun `takes only the first two words from a longer name`() {
        // Implementation uses split(" ").take(2). A three-word name therefore
        // produces initials of the first two tokens, not first + last.
        assertEquals("JM", "John Michael Smith".getNameInitials())
    }
}
