/*
 * Copyright (c) Mikolaj Olszewski 2026-11-6.
 */
package eu.mikus.edziennik.config

import eu.mikus.edziennik.data.db.AppDb
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MarkerConfig(
    db: AppDb = mockk(relaxed = true),
    profileId: Int? = null,
) : BaseConfig(db, profileId, entries = emptyList())

private class RetirableConfig : BaseConfig(mockk(relaxed = true), 7, emptyList()) {
    fun retireNow() = retireInstance()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ParseFailureMarkerTest {

    private val markerKey = "parseFailure.widgetConfigs"

    @Test
    fun `a recorded failure lands under the prefixed key`() {
        val config = MarkerConfig()

        config.recordParseFailure("widgetConfigs", "{oops")

        assertEquals("{oops", config.values[markerKey])
        assertTrue(config.has(markerKey))
    }

    @Test
    fun `the property's own row is never touched`() = runTest {
        val config = MarkerConfig()
        config.values["widgetConfigs"] = "{oops"
        val seen = mutableListOf<String>()
        val job = launch { config.changes.collect { seen += it } }
        runCurrent()

        config.recordParseFailure("widgetConfigs", "{oops")
        runCurrent()

        assertEquals("{oops", config.values["widgetConfigs"])
        assertFalse("widgetConfigs" in seen)
        assertTrue(markerKey in seen)
        job.cancel()
    }

    @Test
    fun `a stored value longer than the limit is truncated`() {
        val config = MarkerConfig()
        val stored = "x".repeat(400) + "y".repeat(100)

        config.recordParseFailure("widgetConfigs", stored)

        val marker = config.values[markerKey]
        assertEquals(BaseConfig.PARSE_FAILURE_VALUE_LIMIT, marker?.length)
        assertEquals(stored.take(BaseConfig.PARSE_FAILURE_VALUE_LIMIT), marker)
    }

    @Test
    fun `a marker that is already correct is not written again`() = runTest {
        val config = MarkerConfig()
        val seen = mutableListOf<String>()
        val job = launch { config.changes.collect { seen += it } }
        runCurrent()

        config.recordParseFailure("widgetConfigs", "{oops")
        config.recordParseFailure("widgetConfigs", "{oops")
        runCurrent()

        assertEquals(1, seen.count { it == markerKey })
        job.cancel()
    }

    @Test
    fun `a marker whose stored value changed is rewritten`() = runTest {
        val config = MarkerConfig()
        val seen = mutableListOf<String>()
        val job = launch { config.changes.collect { seen += it } }
        runCurrent()

        config.recordParseFailure("widgetConfigs", "{oops")
        config.recordParseFailure("widgetConfigs", "[bad")
        runCurrent()

        assertEquals(2, seen.count { it == markerKey })
        assertEquals("[bad", config.values[markerKey])
        job.cancel()
    }

    @Test
    fun `a retired instance refuses the marker without throwing`() {
        val config = RetirableConfig().apply { retireNow() }
        assertFalse(config.has("parseFailure.theme"))

        config.recordParseFailure("theme", "dark")

        assertFalse(config.has("parseFailure.theme"))
    }
}
