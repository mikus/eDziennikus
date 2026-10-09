/*
 * Copyright (c) Mikolaj Olszewski 2026-10-6.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.config.Config
import eu.mikus.edziennik.config.ProfileConfig
import eu.mikus.edziennik.config.configFlow
import eu.mikus.edziennik.data.api.models.Update
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.utils.models.Time
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeInputsTest {

    /**
     * The completeness gate: every config key behind a [HomeInputs] field must both reach
     * [readHomeInputs] and change the value it returns, or `configFlow`'s `distinctUntilChanged`
     * (ConfigFlow.kt:27) drops the write and Home never re-derives. That is all nine keys, one
     * `step(...)` each and named by key: five per-profile (`homeCardsLocked`, `agendaSubjectImportant`,
     * `homeEventsWeeks`, `homeEventsLimit`, `homeGradesWeeks`) and four global (`update`,
     * `bellSyncDiff`, `bellSyncMultiplier`, `countInSeconds`). Sampling one key per tree would not do:
     * a reader that captured a key instead of re-reading it still compiles and still renders
     * correctly on cold start, and silently never updates.
     *
     * Three things this deliberately does NOT fake:
     * - **Both config instances.** `App.config` and `App.profile.config` are distinct `BaseConfig`
     *   instances with separate value maps, so the real [Config] and [ProfileConfig] are both built
     *   here and driven through the real [configFlow]. A reader that subscribes to one and reads the
     *   other misses half its changes.
     * - **Every write goes through its delegate setter**, never `profile.set("homeEventsLimit", "7")`.
     *   `ConfigDelegate.getValue` caches after first read (DelegateConfig.kt:60-61, :80-82) and
     *   `BaseConfig.set` does not invalidate that cache (BaseConfig.kt:56-62). `configFlow`'s
     *   `onStart` primes the delegates before the first write here, so a raw `set()` would emit a key
     *   whose re-read returns the stale value — `distinctUntilChanged` would drop it and every
     *   assertion below would fail against correct code. Only `setValue` (DelegateConfig.kt:74-78)
     *   updates the cache and emits together.
     * - **`bellSyncDiffMillis` is one field fed by TWO keys.** `ConfigTimetable.kt:12-13` defaults
     *   `bellSyncMultiplier` to 0 and `bellSyncDiff` to null, and the reader multiplies them, so from
     *   defaults the field is 0L whichever single key is written. The `bellSyncDiff` and
     *   `bellSyncMultiplier` steps are ordered and valued so that each one actually moves the field;
     *   the comments on them say how. Do not "simplify" either back to a lone write.
     *
     * Asserting per field by name (rather than on an emission count) is what makes deleting a
     * [HomeInputs] field a compile failure instead of a silent regression. `step` also refuses a step
     * whose expected value equals the current one, because such a step would pass without proving
     * anything.
     *
     * `notPublic` is NOT config — it is a captured parameter of [readHomeInputs] — so it is passed
     * as a constant and is not under test here.
     */
    @Test
    fun `every HomeInputs field tracks its config key, across both instances`() = runTest {
        val scope = this
        val db = mockk<AppDb>(relaxed = true)
        every { db.configDao().getAllNow() } returns emptyList()
        val global = Config(db)                          // Config has no init block of its own
        val profile = ProfileConfig(db, 1, emptyList())  // entries != null skips getAllNow

        val seen = mutableListOf<HomeInputs>()
        val job = scope.launch {
            configFlow(global, profile) {
                readHomeInputs(global, profile, notPublic = false)
            }.collect { seen += it }
        }
        scope.runCurrent()
        assertEquals(1, seen.size, "configFlow must prime before any write")

        fun step(field: String, write: () -> Unit, expected: (HomeInputs) -> HomeInputs) {
            val before = seen.last()
            val after = expected(before)
            assertNotEquals(
                before, after,
                "$field: expected value equals the current one, so this step proves nothing",
            )
            write()
            scope.runCurrent()
            assertEquals(after, seen.last(), "writing $field did not reach HomeInputs")
        }

        // Per-profile tree.
        step("homeCardsLocked", { profile.ui.homeCardsLocked = true }) { it.copy(locked = true) }
        step("agendaSubjectImportant", { profile.ui.agendaSubjectImportant = true }) {
            it.copy(config = it.config.copy(agendaSubjectImportant = true))
        }
        step("homeEventsWeeks", { profile.ui.homeEventsWeeks = 6 }) {
            it.copy(config = it.config.copy(homeEventsWeeks = 6))
        }
        step("homeEventsLimit", { profile.ui.homeEventsLimit = 7 }) {
            it.copy(config = it.config.copy(homeEventsLimit = 7))
        }
        step("homeGradesWeeks", { profile.ui.homeGradesWeeks = 3 }) {
            it.copy(config = it.config.copy(homeGradesWeeks = 3))
        }

        // Global tree, on the OTHER instance: these four fail if the global Config is not subscribed
        // to, or if the reader takes them off the profile.
        //
        // Any versionCode above BuildConfig.VERSION_CODE flips updateAvailable false -> true.
        val newer = Update(
            versionCode = BuildConfig.VERSION_CODE + 1,
            versionName = "99.0.0",
            releaseDate = "2026-10-06",
            releaseNotes = null,
            releaseType = "release",
            isOnGooglePlay = false,
            downloadUrl = null,
            updateMandatory = false,
        )
        step("update", { global.update = newer }) { it.copy(updateAvailable = true) }

        // The bell-sync pair. bellSyncDiffMillis is `diff in seconds * 1000 * multiplier`, and 0L
        // when diff is null; the defaults are multiplier 0 / diff null (ConfigTimetable.kt:12-13).
        // So writing ONLY the diff leaves the product at 0 (multiplier 0), and writing ONLY the
        // multiplier leaves it at 0 (diff null): the field would not move, `distinctUntilChanged`
        // would drop the emission, and the step would assert 0L == 0L against a reader that never
        // tracks the key. Hence this step sets BOTH, and its expected value is non-zero:
        // 2 min 30 s * 1 = 150_000 ms. A reader that captured the diff (null) or the multiplier
        // (0) stays at 0L and fails here.
        step("bellSyncDiff", {
            global.timetable.bellSyncMultiplier = 1
            global.timetable.bellSyncDiff = Time(0, 2, 30)
        }) {
            it.copy(config = it.config.copy(bellSyncDiffMillis = 150_000L))
        }
        // Now that the diff is set and the multiplier is 1, flipping ONLY the multiplier to -1 moves
        // the field by itself, and the sign flip is what proves the multiplier is re-read: a reader
        // that does not re-read it stays at +150_000 and fails here.
        step("bellSyncMultiplier", { global.timetable.bellSyncMultiplier = -1 }) {
            it.copy(config = it.config.copy(bellSyncDiffMillis = -150_000L))
        }
        step("countInSeconds", { global.timetable.countInSeconds = true }) {
            it.copy(config = it.config.copy(countInSeconds = true))
        }

        job.cancel()
    }
}
