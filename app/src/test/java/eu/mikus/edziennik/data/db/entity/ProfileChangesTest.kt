/*
 * Copyright (c) Mikolaj Olszewski 2026-10-31.
 */
package eu.mikus.edziennik.data.db.entity

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.utils.models.Date
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins [snapshot] and [changesSince] over every stored column of [Profile], found by reflection, so a
 * column added to `Profile` but not to the column list in `ProfileChanges.kt` fails here.
 *
 * [filled] sets every column to a value that is not a fresh profile's, so a column a snapshot misses
 * reads as a change in one test and as a lost value in the other.
 */
class ProfileChangesTest {

    /** What a row is keyed and typed by, plus `studentData`, which is compared per key and not as a column. */
    private val notColumns = setOf("id", "loginStoreId", "loginStoreType", "studentData")

    private val columns: List<Field> = Profile::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) }
        .filter { it.name !in notColumns }
        .sortedBy { it.name }
        .onEach { it.isAccessible = true }

    private fun fresh(studentData: JsonObject = JsonObject()) =
        Profile(1, 2, LoginType.LIBRUS, studentData = studentData)

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    /** A profile whose every column holds a value derived from [tag] that a fresh profile does not. */
    private fun filled(tag: String): Profile {
        val profile = fresh()
        columns.forEachIndexed { index, field ->
            field.set(
                profile,
                when (field.type) {
                    String::class.java -> "$tag-${field.name}"
                    Int::class.javaPrimitiveType, Int::class.javaObjectType -> 4100 + index
                    Long::class.javaPrimitiveType, Long::class.javaObjectType -> 4100L + index
                    Boolean::class.javaPrimitiveType -> !(field.get(fresh()) as Boolean)
                    Date::class.java -> Date(2001, 2, 3 + index)
                    List::class.java -> listOf(4100L + index)
                    else -> error("no filled value for ${field.name}: ${field.type}")
                },
            )
        }
        return profile
    }

    /** A value of the same type as [value] that is not equal to it. */
    private fun other(value: Any?): Any = when (value) {
        is String -> "$value-other"
        is Int -> value + 1
        is Long -> value + 1
        is Boolean -> !value
        is Date -> Date(value.year, value.month, value.day + 1)
        is List<*> -> value + 99L
        else -> error("no other value for $value")
    }

    /** So that a loop over [columns] cannot pass because reflection found none of them. */
    private fun assertColumnsFound() {
        val names = columns.map { it.name }
        listOf("name", "studentNumber", "dateYearEnd", "disabledNotifications", "lastReceiversSync")
            .forEach { assertTrue(it in names, "reflection did not find column $it in $names") }
    }

    @Test
    fun `every column the task changed is applied`() {
        assertColumnsFound()

        val failing = columns.filter { field ->
            val task = filled("A")
            val base = task.snapshot()
            val changed = other(field.get(task))
            field.set(task, changed)
            val target = filled("A")

            task.changesSince(base).applyTo(target)

            field.get(target) != changed
        }.map { it.name }

        assertEquals(emptyList(), failing)
    }

    @Test
    fun `every column the task left alone keeps the target's value`() {
        assertColumnsFound()

        val failing = columns.filter { field ->
            val task = filled("A")
            val base = task.snapshot()
            val target = filled("A")
            val targets = other(field.get(target))
            field.set(target, targets)

            task.changesSince(base).applyTo(target)

            field.get(target) != targets
        }.map { it.name }

        assertEquals(emptyList(), failing)
    }

    @Test
    fun `studentData changes apply key by key`() {
        val task = fresh(json("""{"keep":1,"set":1,"drop":1,"nul":"x"}"""))
        val base = task.snapshot()
        task.studentData.addProperty("set", 2)
        task.studentData.addProperty("new", 3)
        task.studentData.remove("drop")
        task.studentData.add("nul", JsonNull.INSTANCE)
        val target = fresh(json("""{"keep":9,"set":1,"drop":1,"nul":"x","ui":"u"}"""))

        task.changesSince(base).applyTo(target)

        assertEquals(9L, target.studentData.get("keep").asLong)
        assertEquals(2L, target.studentData.get("set").asLong)
        assertEquals(3L, target.studentData.get("new").asLong)
        assertFalse(target.studentData.has("drop"))
        assertTrue(target.studentData.get("nul") is JsonNull)
        assertEquals("u", target.studentData.get("ui").asString)
    }

    @Test
    fun `applyColumnsTo leaves studentData alone`() {
        val task = fresh(json("""{"keep":1,"set":1,"drop":1,"nul":"x"}"""))
        val base = task.snapshot()
        task.studentData.addProperty("set", 2)
        task.studentData.addProperty("new", 3)
        task.studentData.remove("drop")
        task.studentData.add("nul", JsonNull.INSTANCE)
        task.name = "N"
        val target = fresh(json("""{"keep":9,"set":1,"drop":1,"nul":"x","ui":"u"}"""))
        val studentData = target.studentData
        val before = studentData.deepCopy()

        task.changesSince(base).applyColumnsTo(target)

        assertEquals("N", target.name)
        assertSame(studentData, target.studentData)
        assertEquals(before, target.studentData)
    }

    @Test
    fun `a snapshot shares nothing mutable with its source`() {
        val source = filled("A")
        val snap = source.snapshot()

        source.dateYearEnd.day = 28
        source.studentData.addProperty("x", 1)

        assertNotEquals(28, snap.dateYearEnd.day)
        assertFalse(snap.studentData.has("x"))
    }

    @Test
    fun `a number re-set to its own value is not a change`() {
        val task = fresh(json("""{"unitId":5}"""))
        val base = task.snapshot()
        task.studentData.addProperty("unitId", 5L)
        val target = fresh(json("""{"unitId":9}"""))

        task.changesSince(base).applyTo(target)

        assertEquals(9L, target.studentData.get("unitId").asLong)
    }
}
