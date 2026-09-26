/*
 * Copyright (c) Mikolaj Olszewski 2026-9-26.
 */

package eu.mikus.edziennik.ui.grades

import eu.mikus.edziennik.config.Config
import eu.mikus.edziennik.config.ProfileConfig
import eu.mikus.edziennik.config.configFlow
import eu.mikus.edziennik.data.db.AppDb
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradesInputsTest {

    /**
     * The completeness gate: every config key behind a [GradesInputs] field must both reach
     * [readGradesInputs] and change the value it returns, or `configFlow`'s `distinctUntilChanged`
     * (ConfigFlow.kt:27) drops the write and the screen never re-derives. That is eight of the nine
     * keys `GradesConfigDialog` writes, plus `hideNoGrade` and `hideSticksFromOld`, which the builder
     * reads but the dialog does not write.
     *
     * Two things this deliberately does NOT fake:
     * - **Both config instances.** `orderBy` is the one GLOBAL grades key (ConfigGrades.kt:12); the
     *   other nine are per-profile. A reader that subscribes to one and reads the other misses half
     *   its changes, so the real [Config] and [ProfileConfig] are built here and driven through the
     *   real [configFlow].
     * - **Every write goes through its delegate setter**, never `profile.set("plusValue", "2.0")`.
     *   `ConfigDelegate.getValue` caches after first read (DelegateConfig.kt:60-61, :80-82) and
     *   `BaseConfig.set` does not invalidate that cache (BaseConfig.kt:56-62). `configFlow`'s
     *   `onStart` primes every delegate before the first write here, so a raw `set()` would emit a key
     *   whose re-read returns the stale value — `distinctUntilChanged` would drop it and every
     *   assertion below would fail against correct code. Only `setValue` (DelegateConfig.kt:74-78)
     *   updates the cache and emits together.
     *
     * Asserting per field by name (rather than on an emission count) is what makes deleting a
     * [GradesInputs] field a compile failure instead of a red test.
     *
     * The dialog's ninth key, `colorMode`, is absent on purpose: it never reaches the builder, so it
     * has its own flow in `GradesListFragment` and no JVM gate at all.
     */
    @Test
    fun `every GradesInputs field tracks its config key, across both instances`() = runTest {
        val scope = this
        val db = mockk<AppDb>(relaxed = true)
        every { db.configDao().getAllNow() } returns emptyList()
        val global = Config(db)                          // Config has no init block of its own
        val profile = ProfileConfig(db, 1, emptyList())  // entries != null skips getAllNow

        val seen = mutableListOf<GradesInputs>()
        val job = scope.launch {
            configFlow(global, profile) {
                readGradesInputs(global.grades, profile, isUniversity = false, devMode = true)
            }.collect { seen += it }
        }
        scope.runCurrent()
        assertEquals(1, seen.size, "configFlow must prime before any write")

        fun step(field: String, write: () -> Unit, expected: (GradesInputs) -> GradesInputs) {
            val before = seen.last()
            write()
            scope.runCurrent()
            assertEquals(expected(before), seen.last(), "writing $field did not reach GradesInputs")
        }

        step("plusValue", { profile.grades.plusValue = 2f }) { it.copy(plusValue = 2f) }
        step("minusValue", { profile.grades.minusValue = 0.25f }) { it.copy(minusValue = 0.25f) }
        step("averageWithoutWeight", { profile.grades.averageWithoutWeight = false }) {
            it.copy(averageWithoutWeight = false)
        }
        step("yearAverageMode", { profile.grades.yearAverageMode = 0 }) { it.copy(yearAverageMode = 0) }
        step("dontCountEnabled", { profile.grades.dontCountEnabled = true }) { it.copy(dontCountEnabled = true) }
        step("dontCountGrades", { profile.grades.dontCountGrades = listOf("nb") }) {
            it.copy(dontCountGrades = listOf("nb"))
        }
        step("hideImproved", { profile.grades.hideImproved = true }) {
            it.copy(config = it.config.copy(hideImproved = true))
        }
        step("hideNoGrade", { profile.grades.hideNoGrade = true }) {
            it.copy(config = it.config.copy(hideNoGrade = true))
        }
        // devMode = true above is what makes this observable at all: the builder field is
        // `hideSticksFromOld && devMode`, so with devMode off the write is correctly invisible.
        step("hideSticksFromOld", { profile.grades.hideSticksFromOld = true }) {
            it.copy(config = it.config.copy(hideSticksFromOldDevMode = true))
        }
        // Last, and on the OTHER instance: this is the one that fails if the global Config is not
        // subscribed to, or if the reader takes orderBy off the profile.
        step("orderBy", { global.grades.orderBy = GradesTreeBuilder.ORDER_BY_SUBJECT_ASC }) {
            it.copy(config = it.config.copy(orderBy = GradesTreeBuilder.ORDER_BY_SUBJECT_ASC))
        }

        job.cancel()
    }
}
