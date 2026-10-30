/*
 * Copyright (c) Mikolaj Olszewski 2026-10-30.
 */
package eu.mikus.edziennik.ui.lab

import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.removeProfileData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertIs

/**
 * Pins what LAB writes to the profile row when it edits the profile or its `studentData`, and what
 * its Full sync and Unarchive actions write. The view model is built the way production builds it,
 * through [LabViewModel.Factory], against the real `App` and the real `App.db`, a process-wide
 * database that outlives each test: [setUp] asserts that no row has a fixture id (the profile's own,
 * and the one a refused id edit tries to move it to) and [tearDown] removes any row with either.
 *
 * The fixture is a screen's stale copy: `App.profile`, which LAB shows and edits, is loaded with
 * `studentData {"unitId":4}` and `empty = true`, while the stored row has `studentData
 * {"unitId":5,"startPointsSemester1":100}` and `empty = false`, as it is once a sync has run since
 * the copy was loaded. A write built from the copy puts the copy's values back; a write built from
 * the row keeps the row's. The copy's `studentNumber` is 3 against the row's 7 only so that a write
 * copying an unedited column shows: a sync copies the number back into `App.profile`, so no sync
 * leaves the two apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LabProfilePersistTest {

    private val app: App
        get() = RuntimeEnvironment.getApplication() as App

    private lateinit var previousProfile: Profile
    private var previousLastProfileId = 0

    @Before
    fun setUp() {
        previousProfile = App.profile
        previousLastProfileId = App.config.lastProfileId
        FIXTURE_IDS.forEach {
            assertNull("profile $it left over from another test", App.db.profileDao().getByIdNow(it))
        }
        App.db.profileDao().add(storedProfile())
        app.profileLoad(staleProfile())
    }

    @After
    fun tearDown() {
        try {
            FIXTURE_IDS.forEach { removeProfileData(App.db, App.config, it, keepProfile = false) }
        } finally {
            app.profileLoad(previousProfile)
            App.config.lastProfileId = previousLastProfileId
        }
    }

    @Test
    fun `a LAB field edit lands on the stored row`() {
        vm().writeValue(LabPath(LabRoot.PROFILE, listOf("name")), "Edited")

        // the write is synchronous, so the row is read straight away
        val row = storedRow()
        assertEquals("Edited", row.name)
        assertEquals(storedStudentData(), row.studentData)
        assertFalse(row.empty)
        assertEquals(7, row.studentNumber)
    }

    @Test
    fun `a LAB studentData edit keeps the other keys`() {
        vm().writeValue(LabPath(LabRoot.PROFILE_STUDENT_DATA, listOf("unitId")), "6")

        val expected = JsonObject().apply {
            addProperty("unitId", 6)
            addProperty("startPointsSemester1", 100)
        }
        val row = storedRow()
        assertEquals(expected, row.studentData)
        assertFalse(row.empty)
        assertEquals(7, row.studentNumber)
    }

    @Test
    fun `a LAB edit the stored row cannot take writes nothing`() = runTest {
        // a key only the screen's copy has: the edit resolves on App.profile, then cannot on the row
        app.profileLoad(staleProfile(staleStudentData().apply { addProperty("ghost", 1) }))
        val vm = vm()
        // LabViewModel.effects does not replay, so it is collected before the edit
        val effects = mutableListOf<LabEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }

        vm.writeValue(LabPath(LabRoot.PROFILE_STUDENT_DATA, listOf("ghost")), "2")

        assertIs<LabEffect.ParseFailed>(effects.single())
        val row = storedRow()
        assertEquals("Stored", row.name)
        assertEquals(storedStudentData(), row.studentData)
        assertFalse(row.empty)
    }

    @Test
    fun `a LAB edit whose stored target is not a primitive writes nothing`() = runTest {
        // The sibling above resolves a key the row does not have, so it throws out of
        // LabPathResolver.descend and never reaches persistLabRoot's Unsupported guard. Here the key
        // is present in both and only its TYPE differs - a number on the screen's copy, an object on
        // the row - so the walk succeeds and the guard is what refuses the edit. Only the effect can
        // tell a refusal from a silent no-op: dropping the guard leaves the row untouched either way.
        App.db.profileDao().add(
            storedProfile(JsonObject().apply { add("unitId", JsonObject().apply { addProperty("nested", 1) }) }),
        )
        val vm = vm()
        // LabViewModel.effects does not replay, so it is collected before the edit
        val effects = mutableListOf<LabEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }

        vm.writeValue(LabPath(LabRoot.PROFILE_STUDENT_DATA, listOf("unitId")), "6")

        assertIs<LabEffect.ParseFailed>(effects.single())
        assertEquals(1, storedRow().studentData.getAsJsonObject("unitId").get("nested").asInt)
    }

    @Test
    fun `a LAB edit after a refused id edit still targets the profile LAB opened`() = runTest {
        val vm = vm()
        // LabViewModel.effects does not replay, so it is collected before the edits
        val effects = mutableListOf<LabEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }

        // editProfile refuses an id change, and the edit has already moved App.profile to the new id
        vm.writeValue(LabPath(LabRoot.PROFILE, listOf("id")), REFUSED_ID.toString())

        assertIs<LabEffect.ParseFailed>(effects.single())
        assertEquals("Stored", storedRow().name)
        assertNull(App.db.profileDao().getByIdNow(REFUSED_ID))
        // the premise: were App.profile still at the id LAB opened, the id read at save time and the
        // id captured at open would agree, and the edit below could not tell them apart. If LAB ever
        // rolls a refused edit back in memory, this needs another way to move App.profile off that id
        assertEquals("the refused edit left App.profile at the new id", REFUSED_ID, App.profile.id)

        // the next edit resolves on App.profile, now at the new id, and still saves to the row LAB opened
        vm.writeValue(LabPath(LabRoot.PROFILE, listOf("name")), "X")

        assertEquals(1, effects.size)
        val row = storedRow()
        assertEquals("X", row.name)
        assertEquals(storedStudentData(), row.studentData)
        assertFalse(row.empty)
    }

    @Test
    fun `LAB FullSync marks the row empty and keeps the rest`() {
        performLabAction(app, LabAction.FullSync)

        val row = storedRow()
        assertTrue(row.empty)
        assertEquals(storedStudentData(), row.studentData)
    }

    @Test
    fun `LAB Unarchive clears both archive fields`() {
        App.db.profileDao().add(storedProfile().apply {
            archived = true
            archiveId = 5
        })

        performLabAction(app, LabAction.Unarchive)

        val row = storedRow()
        assertFalse(row.archived)
        assertNull(row.archiveId)
        assertEquals(storedStudentData(), row.studentData)
    }

    private fun vm(): LabViewModel = LabViewModel.Factory(app).create(LabViewModel::class.java)

    private fun storedRow(): Profile =
        requireNotNull(App.db.profileDao().getByIdNow(PROFILE_ID)) { "no profile row $PROFILE_ID" }

    private fun storedStudentData() = JsonObject().apply {
        addProperty("unitId", 5)
        addProperty("startPointsSemester1", 100)
    }

    private fun staleStudentData() = JsonObject().apply { addProperty("unitId", 4) }

    private fun storedProfile(studentData: JsonObject = storedStudentData()) = Profile(
        id = PROFILE_ID,
        loginStoreId = LOGIN_STORE_ID,
        loginStoreType = LoginType.LIBRUS,
        name = "Stored",
        studentData = studentData,
    ).apply {
        empty = false
        studentNumber = 7
    }

    private fun staleProfile(studentData: JsonObject = staleStudentData()) = Profile(
        id = PROFILE_ID,
        loginStoreId = LOGIN_STORE_ID,
        loginStoreType = LoginType.LIBRUS,
        name = "Stored",
        studentData = studentData,
    ).apply {
        empty = true
        studentNumber = 3
    }

    private companion object {
        const val PROFILE_ID = 9021
        const val REFUSED_ID = 9999
        const val LOGIN_STORE_ID = 9121
        val FIXTURE_IDS = listOf(PROFILE_ID, REFUSED_ID)
    }
}
