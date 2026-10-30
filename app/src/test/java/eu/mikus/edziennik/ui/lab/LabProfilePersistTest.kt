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
 * database that outlives each test: [setUp] asserts the fixture id is absent and [tearDown] removes
 * the row it wrote.
 *
 * The fixture is a screen's stale copy: `App.profile`, which LAB shows and edits, is loaded with
 * `studentData {"unitId":4}` and `empty = true`, while the stored row has `studentData
 * {"unitId":5,"startPointsSemester1":100}` and `empty = false`, as it is once a sync has run since
 * the copy was loaded. A write built from the copy puts the copy's values back; a write built from
 * the row keeps the row's.
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
        assertNull("profile $PROFILE_ID left over from another test", App.db.profileDao().getByIdNow(PROFILE_ID))
        App.db.profileDao().add(storedProfile())
        app.profileLoad(staleProfile())
    }

    @After
    fun tearDown() {
        try {
            removeProfileData(App.db, App.config, PROFILE_ID, keepProfile = false)
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
    }

    @Test
    fun `a LAB studentData edit keeps the other keys`() {
        vm().writeValue(LabPath(LabRoot.PROFILE_STUDENT_DATA, listOf("unitId")), "6")

        val expected = JsonObject().apply {
            addProperty("unitId", 6)
            addProperty("startPointsSemester1", 100)
        }
        assertEquals(expected, storedRow().studentData)
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

    private fun storedProfile() = Profile(
        id = PROFILE_ID,
        loginStoreId = LOGIN_STORE_ID,
        loginStoreType = LoginType.LIBRUS,
        name = "Stored",
        studentData = storedStudentData(),
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
        studentNumber = 7
    }

    private companion object {
        const val PROFILE_ID = 9021
        const val LOGIN_STORE_ID = 9121
    }
}
