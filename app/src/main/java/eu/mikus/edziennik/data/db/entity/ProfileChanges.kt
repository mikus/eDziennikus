/*
 * Copyright (c) Mikolaj Olszewski 2026-10-31.
 */
package eu.mikus.edziennik.data.db.entity

import com.google.gson.JsonElement
import kotlin.reflect.KMutableProperty1

/**
 * One stored column of [Profile]: how to read it, write it, and copy a value of it. [copy] is
 * `{ it }` for an immutable value and a real copy for a mutable one, so that two profiles never
 * share a `Date` or a list.
 */
private class Column<T>(
    private val property: KMutableProperty1<Profile, T>,
    private val copy: (T) -> T = { it },
) {
    /** Sets [target]'s value to a copy of [source]'s. */
    fun copyInto(target: Profile, source: Profile) {
        property.set(target, copy(property.get(source)))
    }

    /** Whether the two profiles hold unequal values, compared by `equals`. */
    fun differs(first: Profile, second: Profile): Boolean = property.get(first) != property.get(second)

    /**
     * Takes a copy of [source]'s value now and returns a function that sets a further copy of it on
     * a profile, so that neither a later change to [source] nor an earlier application reaches the
     * next one.
     */
    fun capture(source: Profile): (Profile) -> Unit {
        val value = copy(property.get(source))
        return { target -> property.set(target, copy(value)) }
    }
}

/**
 * The stored `var` columns of [Profile], and the one place a new column must be added.
 * [snapshot] and [changesSince] both read this list, so a column missing from it is neither copied
 * nor compared. `ProfileChangesTest` finds the columns by reflection and fails until the list
 * holds every one.
 *
 * The identity columns (`id`, `loginStoreId`, `loginStoreType`) are not here, and `studentData` is
 * compared per key instead.
 */
private val COLUMNS: List<Column<*>> = listOf(
    Column(Profile::name),
    Column(Profile::subname),
    Column(Profile::studentNameLong),
    Column(Profile::studentNameShort),
    Column(Profile::accountName),
    Column(Profile::image),
    Column(Profile::empty),
    Column(Profile::archived),
    Column(Profile::syncEnabled),
    Column(Profile::userCode),
    Column(Profile::archiveId),
    Column(Profile::studentNumber),
    Column(Profile::studentClassName),
    Column(Profile::studentSchoolYearStart),
    Column(Profile::dateSemester1Start) { it.clone() },
    Column(Profile::dateSemester2Start) { it.clone() },
    Column(Profile::dateYearEnd) { it.clone() },
    Column(Profile::disabledNotifications) { it?.toList() },
    Column(Profile::lastReceiversSync),
)

/**
 * A deep copy of this profile: every column in [COLUMNS] and `studentData`, sharing no `Date`, list
 * or mutable `JsonElement` (`JsonObject`, `JsonArray`) with the original. A `JsonPrimitive` or
 * `JsonNull` in `studentData` is shared, because Gson's `deepCopy()` returns the same immutable
 * object. A sync task takes one when it starts, as the baseline for [changesSince].
 */
fun Profile.snapshot(): Profile =
    Profile(id, loginStoreId, loginStoreType, studentData = studentData.deepCopy()).also { copy ->
        COLUMNS.forEach { it.copyInto(copy, this) }
    }

/**
 * What this profile holds now that [baseline] did not: each column whose value is unequal, and each
 * `studentData` key that was added or has an unequal value, or that was removed. The values are
 * copied out of this profile when this is called, so changing it afterwards changes nothing here.
 */
fun Profile.changesSince(baseline: Profile): ProfileChanges {
    val columns = COLUMNS.filter { it.differs(this, baseline) }.map { it.capture(this) }
    val keysSet = studentData.entrySet()
        .filter { (key, value) -> !baseline.studentData.has(key) || baseline.studentData.get(key) != value }
        .associate { (key, value) -> key to value.deepCopy() }
    val keysRemoved = baseline.studentData.keySet().filter { !studentData.has(it) }
    return ProfileChanges(columns, keysSet, keysRemoved)
}

/**
 * The difference [Profile.changesSince] found, ready to apply to another copy of the same profile.
 * Whatever it sets on a profile is a copy, so a profile never shares a `Date`, list or mutable
 * `JsonElement` (`JsonObject`, `JsonArray`) with the profile the changes came from or with another
 * profile they were applied to. A `JsonPrimitive` or `JsonNull` is shared, because Gson's
 * `deepCopy()` returns the same immutable object.
 */
class ProfileChanges internal constructor(
    private val columns: List<(Profile) -> Unit>,
    private val keysSet: Map<String, JsonElement>,
    private val keysRemoved: List<String>,
) {
    /** Applies the changed columns, then the added, changed and removed `studentData` keys. */
    fun applyTo(profile: Profile) {
        applyColumnsTo(profile)
        keysSet.forEach { (key, value) -> profile.studentData.add(key, value.deepCopy()) }
        keysRemoved.forEach { profile.studentData.remove(it) }
    }

    /**
     * Applies the changed columns only, and never reads or writes `profile.studentData`. That is by
     * design, for a profile held in memory that must not receive the sync's `studentData` changes.
     */
    fun applyColumnsTo(profile: Profile) {
        columns.forEach { it(profile) }
    }
}
