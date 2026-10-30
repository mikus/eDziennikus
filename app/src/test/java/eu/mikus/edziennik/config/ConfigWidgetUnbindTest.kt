/*
 * Copyright (c) Mikolaj Olszewski 2026-10-12.
 */
package eu.mikus.edziennik.config

import eu.mikus.edziennik.config.db.ConfigDao
import eu.mikus.edziennik.config.db.ConfigEntry
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.data.db.dao.ProfileDao
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.removeProfileData
import eu.mikus.edziennik.ext.getBoolean
import eu.mikus.edziennik.ext.getFloat
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getJsonObject
import eu.mikus.edziennik.ui.widgets.WidgetConfig
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Finding AA: a home-screen widget set up for one child re-pointed at the next child.
 *
 * `Config.widgetConfigs` is a **global** value keyed by `appWidgetId` whose entries each carry their
 * own `profileId`, so it is invisible to anything that sweeps profile-scoped *tables* - the reason
 * this escapes the registry finding T proposes. Profile ids are reused, so after a removal the
 * stored id resolved to whoever took it next, and the widget rendered that child's name, lessons or
 * lucky number.
 *
 * **The fix re-points the entry; it does not delete it.** Deleting it makes the providers'
 * `?: continue` / `?: return@forEach` fire, `updateAppWidget` is then never called, and the launcher
 * goes on showing the last frame it was given - the removed child's data - indefinitely. Re-pointed
 * at a marker no profile can hold, the providers reach the "profile does not exist" state they
 * already render today, which on the timetable widget is a message telling the user to remove the
 * widget and add it again.
 *
 * `-1` is **not** that marker and must never be written here: the timetable widget reads it as "all
 * profiles" (`WidgetTimetableProvider.kt:156`) and every notifications widget already stores it,
 * because `WidgetConfigActivity` never asks a notifications widget to pick a profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigWidgetUnbindTest {

    /**
     * One map behind every read and every write.
     *
     * `@Synchronized` throughout because the unbind writes through `BaseConfig.set`, which persists
     * on `launch(Dispatchers.IO)`: a pool thread calling [add] while the test thread is inside
     * [clear] would otherwise throw `ConcurrentModificationException` at random.
     */
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

    private companion object {
        /**
         * Widget 10 belongs to the child about to be removed, and carries the legacy `"opacity"`
         * key that [WidgetConfig] deliberately stopped binding. 11 is a unified timetable widget -
         * the same `-1` every notifications widget stores. 12 belongs to the sibling who stays.
         * 16 is the same child's second widget, stored sparsely - one child commonly has both a
         * timetable and a lucky-number widget.
         */
        const val WIDGETS =
            """{"10":{"profileId":2,"bigStyle":true,"darkTheme":true,"alpha":0.4,"opacity":0.6},""" +
            """"11":{"profileId":-1,"bigStyle":false,"darkTheme":false,"alpha":1.0},""" +
            """"12":{"profileId":3,"bigStyle":false,"darkTheme":false,"alpha":1.0},""" +
            """"16":{"profileId":2}}"""
    }

    private val configDao = FakeConfigDao()
    private val profileDao = mockk<ProfileDao>(relaxed = true)

    private lateinit var db: AppDb
    private lateinit var config: Config

    @BeforeTest
    fun setUp() {
        db = mockk(relaxed = true)
        every { db.configDao() } returns configDao
        every { db.profileDao() } returns profileDao
        every { profileDao.getByIdNow(2) } returns
            Profile(id = 2, loginStoreId = 7, loginStoreType = LoginType.LIBRUS)

        // the widgets, stored before this process started
        configDao.add(ConfigEntry(-1, "widgetConfigs", WIDGETS))
        config = Config(db)
    }

    private fun widget(appWidgetId: String) = config.widgetConfigs.getJsonObject(appWidgetId)

    /** A [Config] over its own store, for the cases that need a different blob than [WIDGETS]. */
    private fun configWith(widgets: String?): Config {
        val dao = FakeConfigDao()
        if (widgets != null)
            dao.add(ConfigEntry(-1, "widgetConfigs", widgets))
        val localDb = mockk<AppDb>(relaxed = true)
        every { localDb.configDao() } returns dao
        return Config(localDb)
    }

    @Test
    fun `a widget bound to the removed child stops resolving to a profile`() {
        assertEquals(2, widget("10").getInt("profileId"))   // anti-vacuity: the seeding worked

        config.removeProfile(2)

        assertEquals(WidgetConfig.PROFILE_REMOVED, widget("10").getInt("profileId"))
    }

    /**
     * The marker has to be a value no profile can ever hold, and `-1` is already taken. A marker of
     * `-1` would turn this child's timetable widget into an all-children one - which shows the next
     * child, recreating the defect by another route.
     */
    @Test
    fun `the removed marker can never name a real profile`() {
        assertNotEquals(-1, WidgetConfig.PROFILE_REMOVED)
        // ProfileDao.lastId selects WHERE profileId > 0, and LoginViewModel and ProfileArchiver
        // both mint MAX + 1, so every real id is at least 1
        assertTrue(WidgetConfig.PROFILE_REMOVED < 1)
    }

    @Test
    fun `an all-profiles widget keeps its binding`() {
        assertEquals(-1, widget("11").getInt("profileId"))

        config.removeProfile(2)

        assertEquals(-1, widget("11").getInt("profileId"))
    }

    @Test
    fun `another child's widget keeps its binding`() {
        assertEquals(3, widget("12").getInt("profileId"))

        config.removeProfile(2)

        assertEquals(3, widget("12").getInt("profileId"))
    }

    /**
     * Only `profileId` may change. Round-tripping the entry through [WidgetConfig] would drop the
     * stale `"opacity"` key and rewrite every absent key to its declared default, which is exactly
     * what turns an already-placed widget translucent - see `WidgetConfig.opacity`.
     */
    @Test
    fun `the entry keeps every other stored key`() {
        config.removeProfile(2)

        val entry = widget("10")
        assertEquals(true, entry.getBoolean("bigStyle"))
        assertEquals(true, entry.getBoolean("darkTheme"))
        assertEquals(0.4f, entry.getFloat("alpha"))
        assertEquals(0.6f, entry.getFloat("opacity"))
    }

    /**
     * A child commonly has more than one widget - a timetable and a lucky number - and every one of
     * them has to be re-pointed. A `break` after the first match, or a first-match lookup, leaves
     * the rest bound to an id the next child will take, and no other test here would notice.
     *
     * Entry 16 is stored sparsely, so this also pins that a key absent from the stored JSON stays
     * absent: re-pointing must not fill in `WidgetConfig`'s declared defaults.
     */
    @Test
    fun `every widget bound to the removed child is re-pointed, not just the first`() {
        assertEquals(2, widget("10").getInt("profileId"))
        assertEquals(2, widget("16").getInt("profileId"))

        config.removeProfile(2)

        assertEquals(WidgetConfig.PROFILE_REMOVED, widget("10").getInt("profileId"))
        assertEquals(WidgetConfig.PROFILE_REMOVED, widget("16").getInt("profileId"))
        assertFalse(requireNotNull(widget("16")).has("alpha"))
    }

    /**
     * Removing a profile no widget is bound to must not write the global blob at all, and removing
     * one that is must write it exactly once.
     *
     * Asserted on [Config.changes] rather than on the stored string. `BaseConfig.set` fills that
     * flow with a synchronous `tryEmit`, so it is deterministic under `runTest`, and it is the only
     * thing that can tell "did not write" from "wrote the same bytes" - the stored blob is already
     * compact JSON, so an entry left untouched re-serialises to a byte-identical string and a
     * string comparison here would pass against a version that rewrites it on every removal.
     */
    @Test
    fun `a removal with no matching widget writes nothing`() = runTest {
        val seen = mutableListOf<String>()
        val collector = launch { config.changes.collect { seen += it } }
        runCurrent()
        assertEquals(2, widget("10").getInt("profileId"))   // anti-vacuity: the blob really is there

        config.removeProfile(5)
        runCurrent()
        assertEquals(emptyList<String>(), seen)

        config.removeProfile(2)
        runCurrent()
        assertEquals(listOf("widgetConfigs"), seen)

        collector.cancel()
    }

    /**
     * The LAB "wipe the data, keep the profile" action runs the same function with
     * `keepProfile = true`. The profile still exists, so its widgets must stay bound - which holds
     * only because the unbind lives inside `Config.removeProfile`, below the guard.
     */
    @Test
    fun `the LAB wipe leaves widgets bound`() {
        assertEquals(2, widget("10").getInt("profileId"))

        val removed = removeProfileData(db, config, profileId = 2, keepProfile = true)

        assertFalse(removed)
        assertEquals(2, widget("10").getInt("profileId"))
    }

    /**
     * The blob is free-form, editable from LAB, and has outlived two serialisation changes, so an
     * entry that is not an object has to be skipped rather than cast. `as JsonObject` here would
     * throw inside `withContext(Dispatchers.Default)` on the profile-removal path and take the whole
     * removal down with it.
     */
    @Test
    fun `a malformed widget entry does not break the removal`() {
        val fresh = configWith("""{"10":{"profileId":2},"13":"not an object","14":null}""")

        fresh.removeProfile(2)

        assertEquals(
            WidgetConfig.PROFILE_REMOVED,
            fresh.widgetConfigs.getJsonObject("10").getInt("profileId"),
        )
    }

    /**
     * Gson reads an absent `profileId` as `-1`, i.e. "all profiles", so an entry without the key is
     * a unified widget and must be left alone rather than treated as unbound and marked.
     */
    @Test
    fun `an entry with no profileId is left alone`() {
        val fresh = configWith("""{"15":{"bigStyle":true}}""")
        val before = requireNotNull(fresh.widgetConfigs.getJsonObject("15"))
        assertFalse(before.has("profileId"))

        fresh.removeProfile(2)

        val after = requireNotNull(fresh.widgetConfigs.getJsonObject("15"))
        assertFalse(after.has("profileId"))
    }

    /**
     * The blob is hand-editable from LAB (`LabViewModel.kt:245` writes an arbitrary string into any
     * config key), so `profileId` can hold a string or a boolean. When this test was written
     * `ext.getInt` checked `isJsonPrimitive` and then called `asInt`, and both of those ARE
     * primitives - Gson fell through to `Integer.parseInt(getAsString())` and threw. Phase 68 fixed
     * `getInt` so it no longer throws; this test still pins that `boundProfileId` answers null for
     * those shapes rather than relying on the shared accessor to do it. A throw here would take
     * the whole removal down from inside `withContext(Dispatchers.Default)`, and from phase 67
     * onwards the same loop runs inside `Config.migrate`, where it would take app startup down
     * with it.
     *
     * A numeric string is the exception: `getAsInt` parses it without throwing and the providers
     * resolve it as that profile, so entry 19 must still be re-pointed rather than skipped.
     */
    @Test
    fun `a profileId that is not a number is skipped, and a numeric string still names a profile`() {
        val fresh = configWith(
            """{"10":{"profileId":2},"17":{"profileId":"two"},"18":{"profileId":true},""" +
                """"19":{"profileId":"2"},"20":{"profileId":null}}"""
        )

        fresh.removeProfile(2)

        assertEquals(
            WidgetConfig.PROFILE_REMOVED,
            fresh.widgetConfigs.getJsonObject("10").getInt("profileId"),
        )
        assertEquals(
            WidgetConfig.PROFILE_REMOVED,
            fresh.widgetConfigs.getJsonObject("19").getInt("profileId"),
        )
        assertEquals("two", fresh.widgetConfigs.getJsonObject("17")?.get("profileId")?.asString)
        assertEquals(true, fresh.widgetConfigs.getJsonObject("18")?.get("profileId")?.asBoolean)
        // an explicit null parses to JsonNull, which is not a JsonPrimitive: an unsafe cast in
        // `boundProfileId` would throw here instead of skipping the entry
        assertTrue(fresh.widgetConfigs.getJsonObject("20")?.get("profileId")?.isJsonNull == true)
    }

    /** The common case: most devices have no widgets, and a removal must not create a row. */
    @Test
    fun `a removal on a device with no widgets creates no row`() {
        val fresh = configWith(null)
        assertFalse(fresh.has("widgetConfigs"))   // anti-vacuity: nothing stored yet

        fresh.removeProfile(2)

        assertFalse(fresh.has("widgetConfigs"))
    }
}
