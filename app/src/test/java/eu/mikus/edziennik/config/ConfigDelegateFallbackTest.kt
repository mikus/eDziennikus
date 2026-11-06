/*
 * Copyright (c) Mikolaj Olszewski 2026-11-6.
 */
package eu.mikus.edziennik.config

import com.google.gson.JsonObject
import eu.mikus.edziennik.config.db.ConfigDao
import eu.mikus.edziennik.config.db.ConfigEntry
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.ui.base.enums.NavTarget
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * A stored string that will not parse used to behave three different ways depending on the
 * property's type: a reference type (`JsonObject`, a collection) returned a bare null with no throw,
 * which then escaped into a type the compiler guarantees non-null; a primitive-backed type threw an
 * NPE at the unboxing inside the generated getter; and an enum arm threw inside `deserialize` itself
 * (`NoSuchElementException` for a retired id, NPE for a non-numeric element). All three now read the
 * property's declared default, and record the stored value under `parseFailure.<key>`.
 *
 * The three anti-goal tests pin behaviour that was already correct and must stay so: a valid value,
 * a stored null on a nullable property, and an absent key.
 */
class ConfigDelegateFallbackTest {

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

    private val defaultButtons = setOf(
        NavTarget.HOME,
        NavTarget.TIMETABLE,
        NavTarget.AGENDA,
        NavTarget.GRADES,
        NavTarget.MESSAGES,
        NavTarget.HOMEWORK,
        NavTarget.SETTINGS,
    )

    private fun Config.noMarkers() = values.keys.none { it.startsWith(BaseConfig.PARSE_FAILURE_PREFIX) }

    /** The mode the backlog row does not name: no throw at all, a null in a non-null type. */
    @Test
    fun `a reference-typed property falls back instead of yielding null`() {
        val config = configWith("widgetConfigs" to """{"10":{"profileId":""")

        val read: JsonObject? = config.widgetConfigs

        assertNotNull(read)
        assertEquals(JsonObject(), read)
        assertEquals("""{"10":{"profileId":""", config.values["parseFailure.widgetConfigs"])
    }

    @Test
    fun `an int property falls back instead of throwing`() {
        val config = configWith("theme" to "dark")

        assertEquals(1, config.ui.theme)
    }

    @Test
    fun `a long property falls back instead of throwing`() {
        val config = configWith("lastAppSync" to "soon")

        assertEquals(0L, config.sync.lastAppSync)
    }

    /** `toBooleanStrictOrNull` accepts only exactly `true` / `false`. */
    @Test
    fun `a boolean property falls back instead of throwing`() {
        val config = configWith("widgetBindingsRepaired" to "TRUE")

        assertFalse(config.widgetBindingsRepaired)
    }

    /** 102 is the retired `DEBUG` NavTarget id: the one mode with a no-hand-edit route to a device. */
    @Test
    fun `a retired enum id in a collection falls back instead of throwing`() {
        val config = configWith("miniMenuButtons" to "[1,102]")

        assertEquals(defaultButtons, config.ui.miniMenuButtons)
        assertEquals("[1,102]", config.values["parseFailure.miniMenuButtons"])
    }

    @Test
    fun `a non-numeric enum element falls back instead of throwing`() {
        val config = configWith("miniMenuButtons" to """["HOME"]""")

        assertEquals(defaultButtons, config.ui.miniMenuButtons)
        assertEquals("""["HOME"]""", config.values["parseFailure.miniMenuButtons"])
    }

    @Test
    fun `a malformed collection falls back instead of yielding null`() {
        val config = configWith("miniMenuButtons" to "[1,")

        assertEquals(defaultButtons, config.ui.miniMenuButtons)
        assertEquals("[1,", config.values["parseFailure.miniMenuButtons"])
    }

    @Test
    fun `a valid value is still deserialised`() {
        val config = configWith(
            "theme" to "3",
            "widgetConfigs" to """{"10":{"profileId":2}}""",
            "miniMenuButtons" to "[1,11]",
        )

        assertEquals(3, config.ui.theme)
        assertEquals(2, config.widgetConfigs.getAsJsonObject("10").get("profileId").asInt)
        assertEquals(setOf(NavTarget.HOME, NavTarget.TIMETABLE), config.ui.miniMenuButtons)
        assertEquals(true, config.noMarkers())
    }

    @Test
    fun `a stored null on a nullable property still reads null`() {
        val config = configWith("language" to null)

        assertNull(config.ui.language)
        assertFalse(config.has("parseFailure.language"))
    }

    @Test
    fun `an absent key still reads the default and records nothing`() {
        val config = configWith()

        assertEquals(1, config.ui.theme)
        assertEquals(true, config.noMarkers())
    }

    @Test
    fun `a fallback records a marker naming the key`() {
        val config = configWith("theme" to "dark")

        config.ui.theme

        assertEquals("dark", config.values["parseFailure.theme"])
    }

    /** Counts how often the declared default is produced: equal reads of a constant prove nothing. */
    private class CountingHolder(base: BaseConfig) {
        var defaults = 0
        var blob by base.config<JsonObject> { defaults++; JsonObject() }
    }

    @Test
    fun `the fallback is cached, not re-parsed`() {
        val config = configWith("blob" to """{"10":""")
        val holder = CountingHolder(config)

        holder.blob
        holder.blob

        assertEquals(1, holder.defaults)
    }
}
