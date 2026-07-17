/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.grades

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradeRowTextsTest {
    @Test fun `description present keeps category as its own field`() {
        val r = gradeRowTexts(description = "kraje", category = "sprawdzian", isImprovement = false)
        assertEquals("kraje", r.topText)
        assertEquals("sprawdzian", r.categoryText)
        assertFalse(r.categoryIsImprovement)
    }

    @Test fun `blank description promotes category to topText and empties categoryText`() {
        val r = gradeRowTexts(description = "   ", category = "sprawdzian", isImprovement = false)
        assertEquals("sprawdzian", r.topText)
        assertEquals("", r.categoryText)
    }

    @Test fun `null description and null category yield empty strings`() {
        val r = gradeRowTexts(description = null, category = null, isImprovement = false)
        assertEquals("", r.topText)
        assertEquals("", r.categoryText)
    }

    @Test fun `categoryIsImprovement reflects the flag and categoryText stays raw (unwrapped)`() {
        val r = gradeRowTexts(description = "kraje", category = "sprawdzian", isImprovement = true)
        assertTrue(r.categoryIsImprovement)
        assertEquals("sprawdzian", r.categoryText)
    }
}
