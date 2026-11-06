/*
 * Copyright (c) Mikolaj Olszewski 2026-10-13.
 */
package eu.mikus.edziennik.config

import eu.mikus.edziennik.App
import eu.mikus.edziennik.config.db.ConfigDao
import eu.mikus.edziennik.config.db.ConfigEntry
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.data.db.dao.ProfileDao
import eu.mikus.edziennik.ext.getBoolean
import eu.mikus.edziennik.ext.getFloat
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getJsonObject
import eu.mikus.edziennik.ui.widgets.WidgetConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Finding HH: phase 66 stopped finding AA happening, forward only. A device that removed a profile
 * before it still has a `widgetConfigs` entry naming that id, and ids are reused -
 * `ProfileDao.lastId` is a MAX over the survivors - so the next child to be added inherits it and
 * the widget silently starts rendering that child.
 *
 * **The repair is prophylactic, not curative.** Once the id HAS been reused there is nothing to
 * detect: `{"profileId":2}` is byte-identical whether it was configured for the child who holds id 2
 * now or for the one who held it before, and nothing anywhere records which. What this repairs is
 * the window before that - an entry naming an id nobody holds, which renders "profile does not
 * exist" today and would start rendering the next child the moment one is added. Pinning it at
 * `PROFILE_REMOVED` keeps it in the state it is already in.
 *
 * Every test drives `config.migrate(app)`, never the private sweep, so that deleting the call from
 * `migrate` turns this whole class red rather than nothing at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigWidgetRepairTest {

    /** One map behind every read and every write, so a write and the next read share a store. */
    private class FakeConfigDao : ConfigDao {
        val rows = linkedMapOf<Pair<Int, String>, String?>()

        @Synchronized
        override fun add(entry: ConfigEntry) {
            rows[entry.profileId to entry.key] = entry.value
        }

        @Synchronized
        override fun addAll(list: List<ConfigEntry>) = list.forEach(::add)

        @Synchronized
        override fun getAllNow(): List<ConfigEntry> =
            rows.map { (key, value) -> ConfigEntry(key.first, key.second, value) }

        @Synchronized
        override fun getAllNow(profileId: Int): List<ConfigEntry> =
            rows.filter { it.key.first == profileId }
                .map { (key, value) -> ConfigEntry(key.first, key.second, value) }

        @Synchronized
        override fun clear(profileId: Int) {
            rows.keys.removeAll { it.first == profileId }
        }
    }

    private val app = mockk<App>(relaxed = true)
    private lateinit var configDao: FakeConfigDao
    private lateinit var profileDao: ProfileDao
    private lateinit var db: AppDb

    /**
     * A [Config] over a store seeded with [widgets], and a profile table holding exactly [liveIds].
     *
     * [liveIds] is always passed explicitly. A relaxed `mockk<ProfileDao>` answers `idsNow` with an
     * empty list, under which EVERY bound id looks orphaned and half of these tests would pass
     * against a sweep that re-points everything it sees.
     */
    private fun configWith(widgets: String?, vararg liveIds: Int): Config {
        configDao = FakeConfigDao()
        // `hash` seeded so `migrate`'s OWN gate is false and `ConfigMigration` is never constructed
        // - which is the state most real devices are in, and the reason the repair sits outside that
        // `if`. It also keeps `ConfigMigration`'s own `hash = "invalid"` write out of `changes`.
        configDao.add(ConfigEntry(-1, "hash", "invalid"))
        if (widgets != null)
            configDao.add(ConfigEntry(-1, "widgetConfigs", widgets))
        profileDao = mockk(relaxed = true)
        every { profileDao.idsNow } returns liveIds.toList()
        db = mockk(relaxed = true)
        every { db.configDao() } returns configDao
        every { db.profileDao() } returns profileDao
        return Config(db)
    }

    private fun Config.widget(appWidgetId: String) = widgetConfigs.getJsonObject(appWidgetId)

    /**
     * The whole point. Child 2 was removed on a build without phase 66's unbind, so the entry still
     * names id 2 while no profile holds it. The widget renders "profile does not exist" today - and
     * would render the next child added, because `LoginViewModel` mints `lastId + 1` and `lastId` is
     * a MAX over the survivors, which is 1.
     */
    @Test
    fun `a widget left bound to a profile that no longer exists is re-pointed`() {
        val config = configWith("""{"10":{"profileId":2}}""", 1)
        assertEquals(2, config.widget("10").getInt("profileId"))   // anti-vacuity: seeding worked

        config.migrate(app)

        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("10").getInt("profileId"))
    }

    /**
     * The sweep must never touch a binding that still resolves. This is also where the repair's
     * limit lives, executably: when the id HAS been reused, the entry below is exactly what a
     * damaged device looks like, and it is indistinguishable from a widget the parent configured
     * for the child who holds id 2 today. Re-pointing on that evidence would break working widgets
     * to maybe fix broken ones.
     */
    @Test
    fun `a widget bound to a profile that still exists is left alone`() {
        val config = configWith("""{"10":{"profileId":2}}""", 1, 2)
        assertEquals(2, config.widget("10").getInt("profileId"))

        config.migrate(app)

        assertEquals(2, config.widget("10").getInt("profileId"))
    }

    /**
     * `-1` is "all profiles" to the timetable widget (`WidgetTimetableProvider.kt:157`) and is what
     * every notifications widget stores, because `WidgetConfigActivity` never asks a notifications
     * widget to pick a profile. No profile holds it, so a sweep that asked "is this id in the table"
     * without the `> 0` guard would mark it removed - turning a working all-children timetable
     * widget into a dead one, and, read the other way, turning a one-child widget into one that
     * shows the new child. That is finding AA recreated by the fix for it.
     */
    @Test
    fun `an all-profiles widget is never swept`() {
        val config = configWith("""{"11":{"profileId":-1}}""", 1)
        assertEquals(-1, config.widget("11").getInt("profileId"))

        config.migrate(app)

        assertEquals(-1, config.widget("11").getInt("profileId"))
    }

    /**
     * A device phase 66 already fixed arrives here with entries at `PROFILE_REMOVED`. No profile
     * holds `-2` either, so the same missing guard would re-point them to the value they already
     * hold - harmless to look at, but it sets `changed` and rewrites the whole blob on a device with
     * nothing wrong with it.
     *
     * Both halves are asserted because the value alone cannot catch the guard going missing:
     * re-pointing `-2` to `-2` leaves the stored bytes identical, so the entry still reads
     * `PROFILE_REMOVED` afterwards and only the write signal on `Config.changes` tells "left alone"
     * from "rewritten with the same thing". Do not delete the `changes` plumbing as redundant - it
     * is the only part of this test that pins the `-2` half of the `> 0` guard.
     */
    @Test
    fun `an entry already marked removed is never swept`() = runTest {
        val config = configWith("""{"10":{"profileId":-2}}""", 1)
        val seen = mutableListOf<String>()
        val collector = launch { config.changes.collect { seen += it } }
        runCurrent()
        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("10").getInt("profileId"))

        config.migrate(app)
        runCurrent()

        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("10").getInt("profileId"))
        assertFalse("widgetConfigs" in seen)
        collector.cancel()
    }

    /**
     * Review Focus 3. An archived profile keeps a positive id and stays in `profiles`, and
     * `getByIdNow` - which is what both providers call to resolve a bound widget - does not filter
     * it, so the widget still renders. `ProfileDao` declares `idsForSyncNow` two lines from
     * `idsNow` and it filters `archived = 0`; reaching for it here would unbind every archived
     * child's widget. The fixture puts ids 1 and 3 in the table, which is what `idsNow` reports
     * for an archived profile, and the test stubs `idsForSyncNow` to `[1]` alone - so the two lists
     * disagree on id 3, and only the one the repair reads keeps this widget bound. Neither the `1`
     * nor the stub is noise; the comment inside the test says why the stub is there.
     */
    @Test
    fun `a widget bound to an archived profile is left alone`() {
        val config = configWith("""{"12":{"profileId":3}}""", 1, 3)
        assertEquals(3, config.widget("12").getInt("profileId"))
        // The pin, made deliberate: an archived profile is in `profiles` but NOT in the sync list,
        // so reaching for `idsForSyncNow` would see id 3 as orphaned and unbind this widget. Without
        // this line the test still catches that, but only via MockK's relaxed default - and a later
        // fixture change that stubs this property would retire the pin with no test going red.
        every { profileDao.idsForSyncNow } returns listOf(1)

        config.migrate(app)

        assertEquals(3, config.widget("12").getInt("profileId"))
    }

    /**
     * Review Focus 2. A parent who removed two children without adding one since has two orphaned
     * ids, and one child commonly has both a timetable and a lucky-number widget, so an id can name
     * more than one entry too. A `firstOrNull` over the ids, or a `break` after the first match
     * inside `unbindWidgets`, passes every single-orphan test above and leaves a widget bound to an
     * id the next child will take. Phase 66 found exactly this slip in its own loop.
     *
     * Entry 16 is stored sparsely, so this also pins that a key absent from the stored JSON stays
     * absent: re-pointing must not fill in `WidgetConfig`'s declared defaults.
     */
    @Test
    fun `every orphaned id is repaired, not just the first`() {
        val config = configWith(
            """{"10":{"profileId":2},"16":{"profileId":2},"14":{"profileId":4},"12":{"profileId":3}}""",
            1, 3,
        )
        assertEquals(2, config.widget("10").getInt("profileId"))
        assertEquals(4, config.widget("14").getInt("profileId"))

        config.migrate(app)

        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("10").getInt("profileId"))
        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("16").getInt("profileId"))
        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("14").getInt("profileId"))
        assertEquals(3, config.widget("12").getInt("profileId"))
        assertFalse(requireNotNull(config.widget("16")).has("alpha"))
    }

    /**
     * Review Focus 4. A parent who removed every child has an empty profile table, so every bound id
     * is orphaned at once. An empty `idsNow` must mean "nothing holds any of these", not "I have no
     * information, bail out" - the second reading leaves exactly the device that has the most stale
     * entries unrepaired.
     */
    @Test
    fun `a device whose profiles were all removed has every widget re-pointed`() {
        val config = configWith("""{"10":{"profileId":2},"12":{"profileId":3}}""")
        assertEquals(2, config.widget("10").getInt("profileId"))

        config.migrate(app)

        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("10").getInt("profileId"))
        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("12").getInt("profileId"))
    }

    /**
     * A device with nothing wrong with it must not have its blob rewritten "to be safe". Asserted on
     * `Config.changes` rather than the stored string: the blob is already compact JSON, so
     * re-persisting an untouched entry produces byte-identical bytes and a value comparison cannot
     * tell "did not write" from "wrote the same thing". `BaseConfig.set` fills that flow with a
     * synchronous `tryEmit` before it queues the row, so it is deterministic under `runCurrent()`.
     *
     * On a device with ten widgets, a needless rewrite is the whole blob re-serialised and a row
     * written on the first launch after every update.
     */
    @Test
    fun `a device whose widgets are all still bound is not written to`() = runTest {
        val config = configWith("""{"10":{"profileId":2},"11":{"profileId":-1}}""", 1, 2)
        val seen = mutableListOf<String>()
        val collector = launch { config.changes.collect { seen += it } }
        runCurrent()
        assertEquals(2, config.widget("10").getInt("profileId"))   // anti-vacuity

        config.migrate(app)
        runCurrent()

        assertFalse("widgetConfigs" in seen)
        collector.cancel()
    }

    /**
     * The cost claim: most devices have no widgets at all, and the repair must not make them pay a
     * query for it. `widgetConfigs` has no row, so the delegate hands back its default `JsonObject()`
     * without even parsing a string, the loop finds nothing, and the profile table is never touched.
     */
    @Test
    fun `a device with no widgets never queries the profile table`() {
        val config = configWith(null)
        assertFalse(config.has("widgetConfigs"))   // anti-vacuity: nothing stored

        config.migrate(app)

        verify(exactly = 0) { profileDao.idsNow }
    }

    /**
     * Only `profileId` may change, end to end through the repair rather than through `removeProfile`.
     * Round-tripping the entry through `WidgetConfig` would drop the stale `"opacity"` key and
     * rewrite every absent key to its declared default - which for `opacity` is the difference
     * between a widget that is fully opaque and one Gson zeroes to fully invisible.
     */
    @Test
    fun `the re-pointed entry keeps every other stored key`() {
        val config = configWith(
            """{"10":{"profileId":2,"bigStyle":true,"darkTheme":true,"alpha":0.4,"opacity":0.6}}""",
            1,
        )

        config.migrate(app)

        val entry = config.widget("10")
        assertEquals(WidgetConfig.PROFILE_REMOVED, entry.getInt("profileId"))
        assertEquals(true, entry.getBoolean("bigStyle"))
        assertEquals(true, entry.getBoolean("darkTheme"))
        assertEquals(0.4f, entry.getFloat("alpha"))
        assertEquals(0.6f, entry.getFloat("opacity"))
    }

    /**
     * The damage class is closed: `profileDao().remove` has exactly one caller, and
     * `config.removeProfile` - which unbinds - runs above it, so no new stale entry can be created.
     * A sweep that kept running would have nothing left to find, while carrying its false-positive
     * exposure on every launch forever.
     *
     * The second half is what makes this falsifiable. Hand-writing the entry back to an orphaned id
     * recreates exactly the state the first `migrate` repaired; a repair that still runs would
     * repair it again, and a test that stopped after the first assertion could not tell.
     */
    @Test
    fun `the repair runs once and never again`() {
        val config = configWith("""{"10":{"profileId":2}}""", 1)

        config.migrate(app)
        assertEquals(WidgetConfig.PROFILE_REMOVED, config.widget("10").getInt("profileId"))

        requireNotNull(config.widget("10")).addProperty("profileId", 2)
        config.migrate(app)

        assertEquals(2, config.widget("10").getInt("profileId"))
    }

    /**
     * Review Focus 5. A flag kept only in a plain in-memory field would make the repair re-run on
     * every launch forever, and `the repair runs once and never again` above cannot see that: both
     * calls are in one process, so a field satisfies it. A raw write into `BaseConfig.values` is
     * not that case - `ConfigDelegate` caches after its first read, so the write never reaches the
     * second `migrate` and both tests go red.
     *
     * The row itself is not assertable here - `BaseConfig.set` persists with
     * `launch(Dispatchers.IO)`, which names its own dispatcher and escapes the test scheduler, so
     * any assertion on the fake DAO would be a timing race. `Config.changes` is the deterministic
     * half of the same write: `set` fills it with a synchronous `tryEmit` before queueing the row,
     * so an emission means a row is on its way, and no emission means no row will ever exist.
     */
    @Test
    fun `the repair records that it ran through the config store`() = runTest {
        val config = configWith("""{"10":{"profileId":2}}""", 1)
        val seen = mutableListOf<String>()
        val collector = launch { config.changes.collect { seen += it } }
        runCurrent()

        config.migrate(app)
        runCurrent()

        // the order matters: the flag is set AFTER the repair, so a failure leaves it unset
        assertEquals(listOf("widgetConfigs", "widgetBindingsRepaired"), seen)
        collector.cancel()
    }

    /**
     * Review Focus 1. `Config.migrate` is the first statement in `App.onCreate`'s background block;
     * `SSLProviderInstaller.install`, `SyncWorker.scheduleNext` and `UpdateWorker.scheduleNext` all
     * follow it inside the same `withContext`, and `App`'s scope is a bare `Job()` with no
     * `CoroutineExceptionHandler`. So a throw here does not merely crash - it crashes at launch,
     * every launch, and silently stops sync ever being scheduled, with no way for the user to reach
     * LAB and undo the edit that caused it.
     *
     * A malformed blob is the shape Task 1's `boundProfileId` cannot reach. Reading `widgetConfigs`
     * does not throw: `ConfigDelegate.getValue` falls back to the declared empty default and records
     * the stored string under `parseFailure.widgetConfigs`. (Before that it returned a bare null from
     * `toJsonObject()` into a non-null-typed property, and the throw came at the first dereference.)
     */
    @Test
    fun `a corrupted widget blob does not take the migration down`() {
        val blob = """{"10":{"profileId":"""
        val config = configWith(blob, 1)

        config.migrate(app)

        // Reached at all only because nothing escaped `migrate` - which is the containment. The
        // assertion is that the row came back untouched: a contained failure must leave the blob
        // exactly as stored, not half-rewritten by a sweep that got part way through it. The marker
        // pins that the fallback both fired and was recorded, so this is not merely "nothing happened".
        assertEquals(blob, configDao.rows[-1 to "widgetConfigs"])
        assertEquals("""{"10":{"profileId":""", config.values["parseFailure.widgetConfigs"])
    }

    /**
     * A behaviour change, not merely a test edit. This used to assert that a repair that failed on a
     * malformed blob is not recorded. `ConfigDelegate` now falls back to an empty default for that
     * blob, so the repair sweeps nothing, succeeds vacuously, and records itself as done forever: a
     * device whose blob is later restored by hand never gets the one-shot sweep.
     *
     * That is accepted. A device whose blob is unparseable has no readable bindings for the sweep to
     * find, and the sweep is prophylactic for a damage class that
     * `Config.removeProfile` (`Config.kt:217-222`) closes at its only caller,
     * `data/db/ProfileDataRemoval.kt:74-79`, where the unbind sits above `profileDao().remove`. The
     * alternative, teaching `ConfigDelegate` to report that a value came from a fallback, adds
     * public API to the delegate for a single caller and is rejected. The original intent, that a
     * failed repair is not recorded, is still covered by
     * `a database failure during the repair is contained too`.
     */
    @Test
    fun `a corrupted widget blob no longer fails the repair`() {
        val config = configWith("""{"10":{"profileId":""", 1)

        config.migrate(app)

        assertTrue(config.widgetBindingsRepaired)
    }

    /**
     * The catch has to be broad, and nothing above pins that: a malformed blob no longer reaches it
     * at all, so narrowing the catch to `NullPointerException` leaves them green while re-opening
     * the case this task exists to close: `idsNow` is a Room query, and a
     * SQLite failure in it would propagate out of `migrate` and take `SSLProviderInstaller` and
     * both `scheduleNext` calls down with it.
     */
    @Test
    fun `a database failure during the repair is contained too`() {
        val config = configWith("""{"10":{"profileId":2}}""", 1)
        every { profileDao.idsNow } throws RuntimeException("database is locked")

        config.migrate(app)

        assertFalse(config.widgetBindingsRepaired)
    }
}
