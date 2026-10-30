/*
 * Copyright (c) Mikołaj Olszewski 2026-10-14.
 */

package eu.mikus.edziennik.ext

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Characterization and delta tests for the typed JSON accessors.
 *
 * Each table renders a read as a `String` so that a returned value and a thrown exception can sit
 * in the same map - the whole surface of one accessor is then a single [assertEquals], and a
 * failure prints every row at once instead of stopping at the first.
 *
 * A Kotlin `null` and the string `"null"` render identically. No key in [SAMPLE] holds the string
 * `"null"`, so no row is ambiguous.
 *
 * These tests drive the real Gson on the app's classpath (2.8.8). They pin Gson's coercion rules
 * as much as our own: if a Gson upgrade changes them, these fail, which is the point.
 *
 * Two cells are JDK-dependent, not Gson-dependent. `(float) 99999999999` is 99999997952 exactly on
 * every JDK, but `Float.toString` renders it `9.9999998E10` up to JDK 18 and `1.0E11` from JDK 19,
 * which adopted shortest-round-trip formatting - both strings parse back to the same float. The
 * values here are JDK 17's, because `app/build.gradle:104` sets `jvmToolchain(17)` and CI runs
 * Temurin 17. If the toolchain ever moves to 19 or later, `getFloat reads every shape` fails on
 * `bigNum` and `qBig` and the fix is to swap those two cells - nothing about the accessors changed.
 */
class JsonExtensionsTest {

    private val json: JsonObject = JsonParser.parseString(SAMPLE).asJsonObject

    private fun probe(keys: List<String> = KEYS, read: (String) -> Any?): Map<String, String> =
        keys.associateWith { key ->
            runCatching { read(key) }.fold(
                onSuccess = { it?.toString() ?: "null" },
                onFailure = { it::class.simpleName ?: "Throwable" },
            )
        }

    @Test
    fun `getInt reads every shape`() {
        assertEquals(
            mapOf(
                "int" to "42",
                "dec" to "42",
                "bigNum" to "1215752191",
                "hugeFloat" to "0",
                "qInt" to "42",
                "qPlus" to "42",
                "qDec" to "null",
                "qBig" to "null",
                "qPad" to "null",
                "qF" to "null",
                "qNaN" to "null",
                "qInf" to "null",
                "qHex" to "null",
                "abc" to "null",
                "empty" to "null",
                "bTrue" to "null",
                "bFalse" to "null",
                "qTrue" to "null",
                "qTRUE" to "null",
                "qFalse" to "null",
                "nul" to "null",
                "obj" to "null",
                "arr" to "null",
                "absent" to "null",
            ),
            probe { json.getInt(it) },
        )
    }

    @Test
    fun `getLong reads every shape`() {
        assertEquals(
            mapOf(
                "int" to "42",
                "dec" to "42",
                "bigNum" to "99999999999",
                "hugeFloat" to "0",
                "qInt" to "42",
                "qPlus" to "42",
                "qDec" to "null",
                "qBig" to "99999999999",
                "qPad" to "null",
                "qF" to "null",
                "qNaN" to "null",
                "qInf" to "null",
                "qHex" to "null",
                "abc" to "null",
                "empty" to "null",
                "bTrue" to "null",
                "bFalse" to "null",
                "qTrue" to "null",
                "qTRUE" to "null",
                "qFalse" to "null",
                "nul" to "null",
                "obj" to "null",
                "arr" to "null",
                "absent" to "null",
            ),
            probe { json.getLong(it) },
        )
    }

    @Test
    fun `getFloat reads every shape`() {
        assertEquals(
            mapOf(
                "int" to "42.0",
                "dec" to "42.5",
                "bigNum" to "9.9999998E10",
                "hugeFloat" to "Infinity",
                "qInt" to "42.0",
                "qPlus" to "42.0",
                "qDec" to "42.5",
                "qBig" to "9.9999998E10",
                "qPad" to "42.0",
                "qF" to "1.0",
                "qNaN" to "NaN",
                "qInf" to "Infinity",
                "qHex" to "null",
                "abc" to "null",
                "empty" to "null",
                "bTrue" to "null",
                "bFalse" to "null",
                "qTrue" to "null",
                "qTRUE" to "null",
                "qFalse" to "null",
                "nul" to "null",
                "obj" to "null",
                "arr" to "null",
                "absent" to "null",
            ),
            probe { json.getFloat(it) },
        )
    }

    @Test
    fun `getBoolean reads every shape`() {
        assertEquals(
            mapOf(
                "int" to "false",
                "dec" to "false",
                "bigNum" to "false",
                "hugeFloat" to "false",
                "qInt" to "false",
                "qPlus" to "false",
                "qDec" to "false",
                "qBig" to "false",
                "qPad" to "false",
                "qF" to "false",
                "qNaN" to "false",
                "qInf" to "false",
                "qHex" to "false",
                "abc" to "false",
                "empty" to "false",
                "bTrue" to "true",
                "bFalse" to "false",
                "qTrue" to "true",
                "qTRUE" to "true",
                "qFalse" to "false",
                "nul" to "null",
                "obj" to "null",
                "arr" to "null",
                "absent" to "null",
            ),
            probe { json.getBoolean(it) },
        )
    }

    @Test
    fun `getString reads every shape`() {
        assertEquals(
            mapOf(
                "int" to "42",
                "dec" to "42.5",
                "bigNum" to "99999999999",
                "hugeFloat" to "1e400",
                "qInt" to "42",
                "qPlus" to "+42",
                "qDec" to "42.5",
                "qBig" to "99999999999",
                "qPad" to " 42 ",
                "qF" to "1f",
                "qNaN" to "NaN",
                "qInf" to "Infinity",
                "qHex" to "0x2A",
                "abc" to "abc",
                "empty" to "",
                "bTrue" to "true",
                "bFalse" to "false",
                "qTrue" to "true",
                "qTRUE" to "TRUE",
                "qFalse" to "false",
                "nul" to "null",
                "obj" to "null",
                "arr" to "null",
                "absent" to "null",
            ),
            probe { json.getString(it) },
        )
    }

    @Test
    fun `getChar reads every shape`() {
        assertEquals(
            mapOf(
                "int" to "4",
                "dec" to "4",
                "bigNum" to "9",
                "hugeFloat" to "1",
                "qInt" to "4",
                "qPlus" to "+",
                "qDec" to "4",
                "qBig" to "9",
                "qPad" to " ",
                "qF" to "1",
                "qNaN" to "N",
                "qInf" to "I",
                "qHex" to "0",
                "abc" to "a",
                "empty" to "null",
                "bTrue" to "t",
                "bFalse" to "f",
                "qTrue" to "t",
                "qTRUE" to "T",
                "qFalse" to "f",
                "nul" to "null",
                "obj" to "null",
                "arr" to "null",
                "absent" to "null",
            ),
            probe { json.getChar(it) },
        )
    }

    @Test
    fun `getInt with a default substitutes only where the value cannot be read`() {
        assertEquals(
            mapOf(
                "int" to "42",
                "bFalse" to "-1",
                "abc" to "-1",
                "obj" to "-1",
                "absent" to "-1",
            ),
            probe(DEFAULT_KEYS) { json.getInt(it, -1) },
        )
    }

    @Test
    fun `getLong with a default substitutes only where the value cannot be read`() {
        assertEquals(
            mapOf(
                "int" to "42",
                "bFalse" to "-1",
                "abc" to "-1",
                "obj" to "-1",
                "absent" to "-1",
            ),
            probe(DEFAULT_KEYS) { json.getLong(it, -1L) },
        )
    }

    @Test
    fun `getFloat with a default substitutes only where the value cannot be read`() {
        assertEquals(
            mapOf(
                "int" to "42.0",
                "bFalse" to "-1.0",
                "abc" to "-1.0",
                "obj" to "-1.0",
                "absent" to "-1.0",
            ),
            probe(DEFAULT_KEYS) { json.getFloat(it, -1f) },
        )
    }

    /**
     * The `bFalse` row is the one that matters: the with-default family is `… ?: defaultValue`,
     * and `?:` fires only on null. A stored `false` must survive a `true` default. It does, and it
     * has to - `LibrusApiUnits.kt:41-42` call `getBoolean("…", true)` from inside an `apply {}`
     * block, so they are receiver-less calls that a `.getBoolean(` grep cannot see.
     */
    @Test
    fun `getBoolean with a default substitutes only where the value cannot be read`() {
        assertEquals(
            mapOf(
                "int" to "false",
                "bFalse" to "false",
                "abc" to "false",
                "obj" to "true",
                "absent" to "true",
            ),
            probe(DEFAULT_KEYS) { json.getBoolean(it, true) },
        )
    }

    @Test
    fun `getString with a default substitutes only where the value cannot be read`() {
        assertEquals(
            mapOf(
                "int" to "42",
                "bFalse" to "false",
                "abc" to "abc",
                "obj" to "D",
                "absent" to "D",
            ),
            probe(DEFAULT_KEYS) { json.getString(it, "D") },
        )
    }

    @Test
    fun `getChar with a default substitutes only where the value cannot be read`() {
        assertEquals(
            mapOf(
                "int" to "4",
                "bFalse" to "f",
                "abc" to "a",
                "obj" to "D",
                "absent" to "D",
            ),
            probe(DEFAULT_KEYS) { json.getChar(it, 'D') },
        )
    }

    /** Every accessor is declared on `JsonObject?`, and nothing else tests that. */
    @Test
    fun `a null receiver reads as null and takes the default`() {
        val nothing: JsonObject? = null
        assertNull(nothing.getInt("k"))
        assertNull(nothing.getLong("k"))
        assertNull(nothing.getFloat("k"))
        assertNull(nothing.getBoolean("k"))
        assertNull(nothing.getString("k"))
        assertNull(nothing.getChar("k"))
        assertNull(nothing.getJsonObject("k"))
        assertNull(nothing.getJsonArray("k"))
        assertEquals(-1, nothing.getInt("k", -1))
        assertEquals(-1L, nothing.getLong("k", -1L))
        assertEquals(-1f, nothing.getFloat("k", -1f))
        assertEquals(true, nothing.getBoolean("k", true))
        assertEquals("D", nothing.getString("k", "D"))
        assertEquals('D', nothing.getChar("k", 'D'))
    }

    /** These two are not changed by this phase, and the split must not move them. */
    @Test
    fun `getJsonObject and getJsonArray read nested nodes and reject the wrong shape`() {
        assertEquals(0, json.getJsonObject("obj")?.size())
        assertEquals(0, json.getJsonArray("arr")?.size())
        assertNull(json.getJsonObject("arr"))
        assertNull(json.getJsonArray("obj"))
        assertNull(json.getJsonObject("int"))
        assertNull(json.getJsonArray("abc"))
        assertNull(json.getJsonObject("nul"))
        assertNull(json.getJsonObject("absent"))
    }

    /**
     * The non-object sits BETWEEN two objects, so a fix that degrades to
     * "return an empty list" fails here while still not throwing.
     */
    @Test
    fun `asJsonObjectList keeps the objects, in order, and skips everything else`() {
        val arr = JsonParser.parseString("""[{"a":1}, 5, null, "s", [], {"a":2}]""").asJsonArray
        assertEquals(listOf(1, 2), arr.asJsonObjectList().map { it.getInt("a") })
    }

    @Test
    fun `asJsonObjectList on an array of objects is unchanged`() {
        val arr = JsonParser.parseString("""[{"a":1},{"a":2},{"a":3}]""").asJsonArray
        assertEquals(listOf(1, 2, 3), arr.asJsonObjectList().map { it.getInt("a") })
    }

    @Test
    fun `asJsonObjectList on an empty array is empty`() {
        assertEquals(emptyList<JsonObject>(), JsonParser.parseString("[]").asJsonArray.asJsonObjectList())
    }

    companion object {
        private val KEYS = listOf(
            "int", "dec", "bigNum", "hugeFloat",
            "qInt", "qPlus", "qDec", "qBig", "qPad", "qF", "qNaN", "qInf", "qHex",
            "abc", "empty",
            "bTrue", "bFalse", "qTrue", "qTRUE", "qFalse",
            "nul", "obj", "arr", "absent",
        )

        private val DEFAULT_KEYS = listOf("int", "bFalse", "abc", "obj", "absent")

        private const val SAMPLE = """
            {
              "int": 42, "dec": 42.5, "bigNum": 99999999999, "hugeFloat": 1e400,
              "qInt": "42", "qPlus": "+42", "qDec": "42.5", "qBig": "99999999999",
              "qPad": " 42 ", "qF": "1f", "qNaN": "NaN", "qInf": "Infinity", "qHex": "0x2A",
              "abc": "abc", "empty": "",
              "bTrue": true, "bFalse": false,
              "qTrue": "true", "qTRUE": "TRUE", "qFalse": "false",
              "nul": null, "obj": {}, "arr": []
            }
        """
    }
}
