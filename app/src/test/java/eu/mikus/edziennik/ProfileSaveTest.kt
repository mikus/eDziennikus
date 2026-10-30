/*
 * Copyright (c) Mikolaj Olszewski 2026-10-30.
 */
package eu.mikus.edziennik

import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.removeProfileData
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins [App.profileSave], the entry point the profile dialogs save through: the edit shows in
 * `App.profile` at once, and is applied in the background to the row as it is stored now.
 *
 * It boots the real `App` and writes the real `App.db`, a process-wide database that outlives each
 * test, so [setUp] asserts the fixture ids are absent and [tearDown] joins every save and removes
 * every row it wrote.
 *
 * The fixture is a screen's stale copy: `App.profile` is loaded with `studentData {"unitId":4}` and
 * `empty = true`, while the stored row has `studentData {"unitId":5,"startPointsSemester1":100}` and
 * `empty = false`, as it is once a sync has run since the copy was loaded. A save built from the
 * copy puts the copy's values back; a save built from the row keeps the row's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileSaveTest {

    private val app: App
        get() = RuntimeEnvironment.getApplication() as App

    private lateinit var previousProfile: Profile
    private var previousLastProfileId = 0
    private val jobs = mutableListOf<Job>()

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
            jobs.forEach(::join)
        } finally {
            FIXTURE_IDS.forEach { removeProfileData(App.db, App.config, it, keepProfile = false) }
            app.profileLoad(previousProfile)
            App.config.lastProfileId = previousLastProfileId
        }
    }

    @Test
    fun `a save edits App profile now and the stored row`() {
        val before = app.profile

        val job = save(PROFILE_ID) { it.name = "Edited" }

        // the in-memory apply is synchronous: it is there before the background write is awaited
        assertEquals("Edited", app.profile.name)
        join(job)
        assertSame(before, app.profile)
        val row = storedRow(PROFILE_ID)
        assertEquals("Edited", row.name)
        assertEquals(storedStudentData(), row.studentData)
        assertFalse(row.empty)
    }

    @Test
    fun `a save for another profile leaves App profile alone`() {
        App.db.profileDao().add(otherProfile())

        join(save(OTHER_ID) { it.name = "X" })

        val row = storedRow(OTHER_ID)
        assertEquals("X", row.name)
        assertEquals(otherStudentData(), row.studentData)
        assertEquals("Stored", app.profile.name)
        assertEquals(staleStudentData(), app.profile.studentData)
    }

    @Test
    fun `a throwing edit does not stop later saves`() {
        // An edit that fails only against the stored row, which is what the background write runs it
        // on: it is applied to App.profile first, and an edit that threw there would surface in the
        // caller instead of reaching the write this test is about.
        join(save(PROFILE_ID) { if (it !== App.profile) error("boom") })

        join(save(PROFILE_ID) { it.name = "After" })

        assertEquals("After", storedRow(PROFILE_ID).name)
    }

    private fun save(profileId: Int, edit: (Profile) -> Unit): Job =
        app.profileSave(profileId, edit).also { jobs += it }

    private fun join(job: Job) {
        runBlocking { withTimeout(5_000) { job.join() } }
    }

    private fun storedRow(profileId: Int): Profile =
        requireNotNull(App.db.profileDao().getByIdNow(profileId)) { "no profile row $profileId" }

    private fun storedStudentData() = JsonObject().apply {
        addProperty("unitId", 5)
        addProperty("startPointsSemester1", 100)
    }

    private fun staleStudentData() = JsonObject().apply { addProperty("unitId", 4) }

    private fun otherStudentData() = JsonObject().apply { addProperty("k", 1) }

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

    private fun staleProfile() = Profile(
        id = PROFILE_ID,
        loginStoreId = LOGIN_STORE_ID,
        loginStoreType = LoginType.LIBRUS,
        name = "Stored",
        studentData = staleStudentData(),
    ).apply {
        empty = true
        studentNumber = 7
    }

    private fun otherProfile() = Profile(
        id = OTHER_ID,
        loginStoreId = OTHER_LOGIN_STORE_ID,
        loginStoreType = LoginType.LIBRUS,
        name = "Other",
        studentData = otherStudentData(),
    )

    private companion object {
        const val PROFILE_ID = 9011
        const val LOGIN_STORE_ID = 9111
        const val OTHER_ID = 9012
        const val OTHER_LOGIN_STORE_ID = 9112
        val FIXTURE_IDS = listOf(PROFILE_ID, OTHER_ID)
    }
}
