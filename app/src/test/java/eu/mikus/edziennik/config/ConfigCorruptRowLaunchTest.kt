/*
 * Copyright (c) Mikolaj Olszewski 2026-11-6.
 */
package eu.mikus.edziennik.config

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.config.db.ConfigDao
import eu.mikus.edziennik.config.db.ConfigEntry
import eu.mikus.edziennik.data.db.AppDb
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

/**
 * The end-to-end consequences of a config row that will not parse, driven through the real entry
 * points rather than through a bare property read, so that deleting the guard in
 * `ConfigDelegate.getValue` turns each of these red.
 *
 * Three of the four are launch paths. `App.onCreate` runs [Config.migrate] on a scope whose job is a
 * bare `Job()` with no `CoroutineExceptionHandler`, so a throw out of it crashed the app on every
 * launch, and the user could not open LAB to undo it. `migrate` reads `dataVersion` and
 * `widgetBindingsRepaired` outside the try that wraps the repair, which is why those two reads were
 * not contained by it.
 */
class ConfigCorruptRowLaunchTest {

    /** One map behind every read and every write. Assertions go on `values`, not on this. */
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

    private fun configWith(vararg seeded: Pair<String, String?>): Config {
        val dao = FakeConfigDao()
        seeded.forEach { (key, value) -> dao.add(ConfigEntry(-1, key, value)) }
        val db = mockk<AppDb>(relaxed = true)
        every { db.configDao() } returns dao
        return Config(db)
    }

    @Test
    fun `a corrupt dataVersion row no longer takes migrate down`() {
        val config = configWith("dataVersion" to "abc")

        config.migrate(mockk<App>(relaxed = true))

        // Reaching here is the point. The marker guards against vacuity: a migrate that never read
        // the property could not have recorded this.
        assertEquals("abc", config.values["parseFailure.dataVersion"])
    }

    @Test
    fun `a corrupt widgetBindingsRepaired row no longer takes migrate down`() {
        val config = configWith("widgetBindingsRepaired" to "TRUE")

        config.migrate(mockk<App>(relaxed = true))

        assertEquals("TRUE", config.values["parseFailure.widgetBindingsRepaired"])
    }

    /**
     * `App.onCreate` reads `config.ui.theme` on the main thread, in no try and no coroutine. This pins
     * the property, not `onCreate` itself, which has no JVM seam here.
     */
    @Test
    fun `a corrupt theme row no longer crashes the Application onCreate read`() {
        val config = configWith("theme" to "dark")

        assertEquals(1, config.ui.theme)
    }

    /**
     * `WidgetConfigActivity` calls `add` on this on the OK tap, from Java, with no Kotlin null check
     * anywhere on that path.
     */
    @Test
    fun `a corrupt widgetConfigs row leaves a placeable widget config`() {
        val config = configWith("widgetConfigs" to "{oops")

        val configs: JsonObject? = config.widgetConfigs

        assertNotNull(configs)
        configs.add("10", JsonObject())
        assertEquals(true, configs.has("10"))
    }
}
