/*
 * Copyright (c) Mikolaj Olszewski 2026-11-11.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins what [ProfileConfigDialog] saves on dismiss: [ProfileConfigChanges]
 * records each field the user changed, with its value, and its edit writes those fields and no
 * other. A change recorded under the wrong field would save that field's value on the stored row,
 * so each field has its own test, and each compares a snapshot of every other field.
 */
class ProfileConfigChangesTest {

    private val gson = Gson()

    @Test
    fun `nothing changed saves nothing`() {
        assertNull(ProfileConfigChanges().edit())
    }

    @Test
    fun `a changed name writes only the name`() {
        val changes = ProfileConfigChanges().apply { name("Renamed") }

        val profile = applyAndAssertRestUnchanged(changes, "name")

        assertEquals("Renamed", profile.name)
    }

    @Test
    fun `a changed sync setting writes only syncEnabled`() {
        val changes = ProfileConfigChanges().apply { syncEnabled(true) }

        val profile = applyAndAssertRestUnchanged(changes, "syncEnabled")

        assertTrue(profile.syncEnabled)
    }

    @Test
    fun `a changed image writes only the image`() {
        val changes = ProfileConfigChanges().apply { image("/files/new.png") }

        val profile = applyAndAssertRestUnchanged(changes, "image")

        assertEquals("/files/new.png", profile.image)
    }

    @Test
    fun `a changed image that is null clears it`() {
        // a null image is a value, not "unchanged"
        val changes = ProfileConfigChanges().apply { image(null) }

        val profile = applyAndAssertRestUnchanged(changes, "image")

        assertNull(profile.image)
    }

    @Test
    fun `the last value recorded for a field is the one saved`() {
        val changes = ProfileConfigChanges().apply {
            name("First")
            name("Second")
            syncEnabled(true)
            syncEnabled(false)
        }

        val profile = applyAndAssertRestUnchanged(changes, "name", "syncEnabled")

        assertEquals("Second", profile.name)
        assertEquals(false, profile.syncEnabled)
    }

    @Test
    fun `a removed profile saves nothing, whatever changed`() {
        val changes = ProfileConfigChanges().apply {
            name("Renamed")
            syncEnabled(true)
            image("/files/new.png")
            removed()
        }

        assertNull(changes.edit())
    }

    /** Applies [changes]' edit to a fully set profile and asserts that only [changing] moved. */
    private fun applyAndAssertRestUnchanged(changes: ProfileConfigChanges, vararg changing: String): Profile {
        val edit = assertNotNull(changes.edit(), "a recorded change must produce an edit")
        val profile = profile()
        val before = gson.toJsonTree(profile).asJsonObject
        edit(profile)
        val after = gson.toJsonTree(profile).asJsonObject
        changing.forEach {
            before.remove(it)
            after.remove(it)
        }
        assertEquals(before, after)
        return profile
    }

    /** Every field set, `syncEnabled` off, so writing any field's default would show. */
    private fun profile() = Profile(
        id = 1,
        loginStoreId = 2,
        loginStoreType = LoginType.LIBRUS,
        name = "Stored",
        subname = "Sub",
        studentNameLong = "Long Name",
        studentNameShort = "L.N.",
        accountName = "Parent",
        studentData = JsonObject().apply { addProperty("unitId", 5) },
    ).apply {
        image = "/files/profile1.png"
        empty = false
        syncEnabled = false
        studentNumber = 7
        studentClassName = "3A"
    }
}
