/*
 * Copyright (c) Mikolaj Olszewski 2026-10-30.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.utils.models.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins which fields each profile dialog's edit builder writes. A dialog saves through its builder,
 * so a builder that also wrote a second field would put that field's stale value on the stored row.
 *
 * Each test applies a builder to a profile with every field set, then compares a Gson snapshot
 * before and after with the named keys removed: whatever is left must be equal, and the named keys
 * are asserted separately. Nullable fields are set because a snapshot of a null field has no key and
 * could not show it change, and `syncEnabled` is off, so a builder that wrote its default would show.
 */
class ProfileEditsTest {

    private val gson = Gson()

    @Test
    fun `studentNumberEdit sets only the number`() {
        val profile = profile()

        val unchanged = applyAndSnapshotRest(profile, studentNumberEdit(12), "studentNumber")

        assertEquals(unchanged.before, unchanged.after)
        assertEquals(12, profile.studentNumber)
    }

    @Test
    fun `profileConfigEdit with only a name`() {
        val profile = profile()

        val unchanged = applyAndSnapshotRest(profile, profileConfigEdit(name = "Renamed"), "name")

        assertEquals(unchanged.before, unchanged.after)
        assertEquals("Renamed", profile.name)
    }

    @Test
    fun `profileConfigEdit with only syncEnabled`() {
        val profile = profile()

        val unchanged = applyAndSnapshotRest(profile, profileConfigEdit(syncEnabled = true), "syncEnabled")

        assertEquals(unchanged.before, unchanged.after)
        assertTrue(profile.syncEnabled)
    }

    @Test
    fun `profileConfigEdit with setImage and a null image clears it`() {
        val profile = profile()

        val unchanged = applyAndSnapshotRest(profile, profileConfigEdit(setImage = true, image = null), "image")

        assertEquals(unchanged.before, unchanged.after)
        assertNull(profile.image)
    }

    @Test
    fun `profileConfigEdit with nothing set`() {
        val profile = profile()

        val none = applyAndSnapshotRest(profile, profileConfigEdit())
        // an image without setImage is not written either: a null image is a value, so only setImage says "write it"
        val unmarkedImage = applyAndSnapshotRest(profile, profileConfigEdit(image = "/files/other.png"))

        assertEquals(none.before, none.after)
        assertEquals(unmarkedImage.before, unmarkedImage.after)
        assertEquals("/files/profile1.png", profile.image)
    }

    private class Snapshots(val before: JsonObject, val after: JsonObject)

    /** Snapshots [profile] before and after [edit], without the [changing] keys. */
    private fun applyAndSnapshotRest(profile: Profile, edit: (Profile) -> Unit, vararg changing: String): Snapshots {
        val before = gson.toJsonTree(profile).asJsonObject
        edit(profile)
        val after = gson.toJsonTree(profile).asJsonObject
        changing.forEach {
            before.remove(it)
            after.remove(it)
        }
        return Snapshots(before, after)
    }

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
        archived = true
        syncEnabled = false
        userCode = "code"
        archiveId = 3
        studentNumber = 7
        studentClassName = "3A"
        studentSchoolYearStart = 2025
        dateSemester1Start = Date(2025, 9, 1)
        dateSemester2Start = Date(2026, 2, 1)
        dateYearEnd = Date(2026, 6, 30)
        disabledNotifications = listOf(1L, 2L)
        lastReceiversSync = 99L
    }
}
