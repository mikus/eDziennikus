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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins [snapshot] and [changesSince] over every stored column of [Profile], found by reflection, so a
 * column added to `Profile` but not to the column list in `ProfileChanges.kt` fails here.
 *
 * [filled] sets every column to a value that is not a fresh profile's, which is what lets each kind of
 * miss show. A column missing from the shared column list fails `every column the task changed is
 * applied`, since `changesSince` skips it too. A column only `snapshot` misses fails `every column
 * the task left alone keeps the target's value`, since the baseline then holds a default and the
 * column reads as changed.
 *
 * A change is captured when `changesSince` is called and copied again when it is applied, so a
 * `Date`, a list or a `studentData` object the task changes later cannot reach what is applied, and
 * two targets do not share what is applied to them. `every column the task changed is applied` pins
 * both for the columns and `studentData changes apply key by key` pins both for a key.
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
        is List<*> -> value.toMutableList().apply { add(99L) }
        else -> error("no other value for $value")
    }

    /** Whether [value] can change in place, so that two profiles sharing it could be told apart. */
    private fun isMutable(value: Any) = value is Date || value is List<*>

    /**
     * Changes [value], which [other] made, in place, and returns an equal copy taken before. A value
     * that cannot change in place is returned as it is.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mutateInPlace(value: Any): Any = when (value) {
        is Date -> value.clone().also { value.day = 27 }
        is MutableList<*> -> value.toList().also { (value as MutableList<Any?>).add(7L) }
        else -> value
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

        val failing = columns.flatMap { field ->
            val task = filled("A")
            val base = task.snapshot()
            val changed = other(field.get(task))
            field.set(task, changed)
            val changes = task.changesSince(base)
            // a change to the task's value after the call must not reach what is applied
            val captured = mutateInPlace(changed)
            val first = filled("A")
            val second = filled("A")

            changes.applyTo(first)
            changes.applyTo(second)

            listOfNotNull(
                "${field.name}: not applied as captured"
                    .takeIf { field.get(first) != captured },
                "${field.name}: two targets share the applied value"
                    .takeIf { isMutable(captured) && field.get(first) === field.get(second) },
            )
        }

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
        task.studentData.add("obj", json("""{"a":1}"""))
        val changes = task.changesSince(base)
        // a change to the task's object after the call must not reach what is applied
        task.studentData.getAsJsonObject("obj").addProperty("a", 2)
        val target = fresh(json("""{"keep":9,"set":1,"drop":1,"nul":"x","ui":"u"}"""))
        val second = fresh(json("""{"keep":9}"""))

        changes.applyTo(target)
        changes.applyTo(second)

        assertEquals(9L, target.studentData.get("keep").asLong)
        assertEquals(2L, target.studentData.get("set").asLong)
        assertEquals(3L, target.studentData.get("new").asLong)
        assertFalse(target.studentData.has("drop"))
        assertTrue(target.studentData.get("nul") is JsonNull)
        assertEquals("u", target.studentData.get("ui").asString)
        assertEquals(
            1L,
            target.studentData.getAsJsonObject("obj").get("a").asLong,
            "key obj: not applied as it was when captured",
        )
        assertNotSame(
            target.studentData.get("obj"),
            second.studentData.get("obj"),
            "key obj: two targets share the applied value",
        )
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
