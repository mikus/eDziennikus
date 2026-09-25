/*
 * Copyright (c) Mikolaj Olszewski 2026-9-25.
 */
package eu.mikus.edziennik.config

import eu.mikus.edziennik.data.db.AppDb
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

private class TestConfig(db: AppDb) : BaseConfig(db, null, emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class BaseConfigChangesTest {

    /**
     * The signal is the whole phase: without it a config write is invisible to a live screen, and the
     * only way to see it is destroying that screen.
     *
     * Reading the value back alongside the key pins what a collector actually depends on: by the time
     * a subscriber sees the key, `values` already holds the new value, so re-reading current state on
     * the signal is safe.
     *
     * It is deliberately NOT sensitive to the order of the two statements inside `set`. Swapping them
     * was mutation-tested and changed nothing, because collection is asynchronous — the write has
     * landed before any subscriber resumes either way. Measured, not assumed.
     */
    @Test
    fun `set emits the key that was written, and the value is readable`() = runTest {
        val config = TestConfig(mockk(relaxed = true))
        val seen = mutableListOf<Pair<String, String?>>()
        val job = launch { config.changes.collect { seen += it to config.values[it] } }
        runCurrent()

        config.set("groupConsecutiveDays", "true")
        config.set("showPresenceInMonth", "false")
        runCurrent()

        val expected: List<Pair<String, String?>> = listOf(
            "groupConsecutiveDays" to "true",
            "showPresenceInMonth" to "false",
        )
        assertEquals(expected, seen)
        job.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigFlowTest {

    /**
     * Delete `.onStart` from [configFlow] and this fails. An unprimed flow in a `combine` leaves the
     * screen on Loading until some unrelated key happens to be written. `withTimeoutOrNull` runs in
     * virtual time, so the failure is immediate rather than a wall-clock hang.
     */
    @Test
    fun `is primed before any config write`() = runTest {
        val flow = configFlow(TestConfig(mockk(relaxed = true))) { 42 }
        assertEquals(42, withTimeoutOrNull(1_000) { flow.first() })
    }

    /** Delete `.distinctUntilChanged` and this fails: an unrelated key re-derives for nothing. */
    @Test
    fun `an unrelated write does not re-emit an equal value`() = runTest {
        val config = TestConfig(mockk(relaxed = true))
        val seen = mutableListOf<Int>()
        val job = launch { configFlow(config) { 42 }.collect { seen += it } }
        runCurrent()

        config.set("someUnrelatedKey", "x")
        runCurrent()

        assertEquals(listOf(42), seen)   // the priming emission only
        job.cancel()
    }

    /**
     * Pass one instance instead of two and this fails. `App.config` and `App.profile.config` are
     * separate objects; a screen that reads both and subscribes to one misses half its changes.
     */
    @Test
    fun `observes every config instance it was given`() = runTest {
        val global = TestConfig(mockk(relaxed = true))
        val profile = TestConfig(mockk(relaxed = true))
        var reads = 0
        val seen = mutableListOf<Int>()
        val job = launch { configFlow(global, profile) { reads++ }.collect { seen += it } }
        runCurrent()

        global.set("a", "1")
        runCurrent()
        profile.set("b", "2")
        runCurrent()

        assertEquals(listOf(0, 1, 2), seen)   // prime, then one re-read per instance
        job.cancel()
    }
}
