/*
 * Copyright (c) Mikolaj Olszewski 2026-10-14.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class LibrusCategoryWeightTest {

    private fun category(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test fun `a category that counts uses its weight`() {
        assertEquals(3f, librusCategoryWeight(category("""{"Weight":3}"""), countToAverage = true))
    }

    @Test fun `a category that counts and genuinely weighs zero is not an error`() {
        assertEquals(0f, librusCategoryWeight(category("""{"Weight":0}"""), countToAverage = true))
    }

    @Test fun `a category that does not count weighs zero`() {
        assertEquals(0f, librusCategoryWeight(category("""{"Weight":3}"""), countToAverage = false))
    }

    @Test fun `a category that does not count weighs zero even with no weight at all`() {
        assertEquals(0f, librusCategoryWeight(category("""{}"""), countToAverage = false))
    }

    /** An absent Weight was never an error - not before phase 68 either, because the accessor's
     *  safe call short-circuited on a missing key. It must keep its long-standing 0f. */
    @Test fun `a counting category with no Weight key at all keeps its long-standing zero`() {
        assertEquals(0f, librusCategoryWeight(category("""{}"""), countToAverage = true))
    }

    @Test fun `a counting category with an explicit JSON null Weight keeps its long-standing zero`() {
        assertEquals(0f, librusCategoryWeight(category("""{"Weight":null}"""), countToAverage = true))
    }

    /** A non-primitive Weight was silent in every prior version too - pre-phase-68 `getFloat`
     *  guarded on `!it.isJsonPrimitive`, so an object or array gave null and then 0f. */
    @Test fun `a counting category whose Weight is an object keeps its long-standing zero`() {
        assertEquals(0f, librusCategoryWeight(category("""{"Weight":{}}"""), countToAverage = true))
    }

    @Test fun `a counting category whose Weight is an array keeps its long-standing zero`() {
        assertEquals(0f, librusCategoryWeight(category("""{"Weight":[]}"""), countToAverage = true))
    }

    /** Phase 69: this is the behaviour change - present, but unreadable. */
    @Test fun `a counting category whose Weight cannot be read fails loudly`() {
        assertFailsWith<IllegalStateException> {
            librusCategoryWeight(category("""{"Weight":"abc"}"""), countToAverage = true)
        }
    }

    @Test fun `a counting category whose Weight is a boolean fails loudly`() {
        assertFailsWith<IllegalStateException> {
            librusCategoryWeight(category("""{"Weight":true}"""), countToAverage = true)
        }
    }

    /** Phase 68's coercion still applies: a quoted number is readable, not malformed. */
    @Test fun `a counting category whose Weight is a quoted number still coerces`() {
        assertEquals(2.5f, librusCategoryWeight(category("""{"Weight":"2.5"}"""), countToAverage = true))
    }
}
