/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import eu.mikus.edziennik.config.BaseConfig
import eu.mikus.edziennik.data.db.enums.LoginType
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class CodecConfig : BaseConfig(db = mockk(relaxed = true), profileId = null, entries = emptyList())

class LabValueCodecTest {

    private fun jsonLeaf(value: Any): LabTarget.JsonLeaf {
        val parent = JsonObject()
        when (value) {
            is String -> parent.addProperty("k", value)
            is Number -> parent.addProperty("k", value)
            is Boolean -> parent.addProperty("k", value)
        }
        return LabTarget.JsonLeaf(parent, "k", parent.get("k") as JsonPrimitive)
    }

    private fun field(old: Any?) = LabTarget.Field(Any(), "f", old)

    private fun configEntry(value: String?): LabTarget.ConfigEntry {
        val owner = CodecConfig().apply { values["k"] = value }
        return LabTarget.ConfigEntry(owner, "k")
    }

    /* ---------- format: the dialog prefill, unquoted ---------- */

    @Test
    fun `format renders JSON primitives without quotes`() {
        assertEquals("hi", LabValueCodec.format(jsonLeaf("hi")))
        assertEquals("2", LabValueCodec.format(jsonLeaf(2)))
        assertEquals("true", LabValueCodec.format(jsonLeaf(true)))
    }

    @Test
    fun `format renders an enum field as its id, not its name`() {
        // LabProfileFragment.kt@53a07964:97 - `objVal.toInt().toString()`.
        assertEquals("2", LabValueCodec.format(field(LoginType.LIBRUS)))
    }

    @Test
    fun `format reads a config value straight out of its owner`() {
        assertEquals("1", LabValueCodec.format(configEntry("1")))
        assertEquals("", LabValueCodec.format(configEntry(null)))
    }

    /* ---------- parse: the JSON arm ---------- */

    @Test
    fun `a fractional JSON number survives an untouched round trip`() {
        // The bug this unit exists to kill: format gave "1.5", the old arm re-parsed with toLong().
        val target = jsonLeaf(1.5)
        val reparsed = LabValueCodec.parse(target, LabValueCodec.format(target)).getOrThrow()
        assertEquals(1.5, reparsed)
    }

    @Test
    fun `a whole JSON number still parses as a Long`() {
        assertEquals(7L, LabValueCodec.parse(jsonLeaf(7), "7").getOrThrow())
    }

    @Test
    fun `non-numeric text into a JSON number fails instead of throwing`() {
        assertIs<LabEditError>(LabValueCodec.parse(jsonLeaf(7), "abc").exceptionOrNull())
    }

    @Test
    fun `JSON strings and booleans parse as they did`() {
        assertEquals("x", LabValueCodec.parse(jsonLeaf("hi"), "x").getOrThrow())
        assertEquals(true, LabValueCodec.parse(jsonLeaf(false), "true").getOrThrow())
        assertEquals(false, LabValueCodec.parse(jsonLeaf(true), "anything else").getOrThrow())
    }

    /* ---------- parse: the config arm ---------- */

    @Test
    fun `the config arm is identity`() {
        // BaseConfig.set takes a String by design; DelegateConfig does the typing on the read side.
        assertEquals("[1,2]", LabValueCodec.parse(configEntry("x"), "[1,2]").getOrThrow())
    }

    /* ---------- parse: the reflective arm ---------- */

    @Test
    fun `every primitive field type round-trips`() {
        val cases = listOf<Any>(7, 7L, 1.5f, 1.5, true, 'x', "hi")
        for (old in cases) {
            val target = field(old)
            assertEquals(old, LabValueCodec.parse(target, LabValueCodec.format(target)).getOrThrow(), "$old")
        }
    }

    @Test
    fun `an enum field round-trips through its id`() {
        val target = field(LoginType.LIBRUS)
        assertEquals(LoginType.LIBRUS, LabValueCodec.parse(target, LabValueCodec.format(target)).getOrThrow())
    }

    @Test
    fun `non-numeric text into a number field fails instead of throwing`() {
        for (old in listOf<Any>(7, 7L, 1.5f, 1.5)) {
            assertIs<LabEditError>(LabValueCodec.parse(field(old), "abc").exceptionOrNull(), "$old")
        }
    }

    @Test
    fun `an empty string into a Char field fails instead of throwing`() {
        // LabProfileFragment.kt@53a07964:128 - `input.toCharArray()[0]`.
        assertIs<LabEditError>(LabValueCodec.parse(field('x'), "").exceptionOrNull())
    }

    @Test
    fun `non-numeric text into an enum field fails before toEnum is reached`() {
        // LabProfileFragment.kt@53a07964:132 has three failure modes, not two: toInt() throws first.
        assertIs<LabEditError>(LabValueCodec.parse(field(LoginType.LIBRUS), "LIBRUS").exceptionOrNull())
    }

    @Test
    fun `an out-of-range id for a known enum fails instead of throwing`() {
        // ext/EnumExtensions.kt:10-16 - seven `first { }` one-liners, one per enum, each throwing
        // NoSuchElementException. This row goes through `asLoginType()` at :13, not the NavTarget
        // one at :16.
        assertIs<LabEditError>(LabValueCodec.parse(field(LoginType.LIBRUS), "9999").exceptionOrNull())
    }

    @Test
    fun `an enum class outside the toEnum table fails instead of throwing`() {
        // ext/EnumExtensions.kt:58 - `else -> throw IllegalArgumentException`. LabToggle is not listed.
        assertIs<LabEditError>(LabValueCodec.parse(field(LabToggle.CHUCKER), "0").exceptionOrNull())
    }

    @Test
    fun `a null field and an unknown field type are refused, not written blind`() {
        // The original's `else -> input` arm (:133) put a String into a field of unknown type and let
        // Field.set throw IllegalArgumentException, uncaught. Design D3 drops that arm.
        assertIs<LabEditError>(LabValueCodec.parse(field(null), "x").exceptionOrNull())
        assertIs<LabEditError>(LabValueCodec.parse(field(listOf(1, 2)), "x").exceptionOrNull())
    }

    @Test
    fun `an Unsupported target refuses with its own reason`() {
        val result = LabValueCodec.parse(LabTarget.Unsupported("arrays are not supported"), "x")
        assertTrue(result.isFailure)
        assertEquals("arrays are not supported", result.exceptionOrNull()?.message)
    }
}
