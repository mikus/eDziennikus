/*
 * Copyright (c) Mikolaj Olszewski 2026-10-30.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import io.mockk.every
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins [AppDb.editProfile]: the edit lands on the row as stored now, so only the fields the edit
 * sets can change, and nothing it does can create a second or a resurrected row.
 *
 * The stored row carries a `studentData` with a unit id and start points, a non-default
 * `studentNumber` and `empty = false`, so a write built from anything but the stored row changes
 * one of them and the test that names it goes red.
 *
 * It needs a real `Context` and a real SQLite file, hence Robolectric (JUnit 4, via Vintage), on a
 * private database file that [AppDb.builder] builds and no other test shares.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ProfileEditTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private var db: AppDb? = null

    @After
    fun closeDb() {
        db?.close()
    }

    @Test
    fun `an edit changes only the fields it sets`() {
        val appDb = openDb()
        appDb.profileDao().add(storedProfile())

        val applied = appDb.editProfile(PROFILE_ID) { it.studentNumber = 12 }

        assertTrue(applied)
        val row = storedRow(appDb, PROFILE_ID)
        assertEquals(12, row.studentNumber)
        assertEquals(storedStudentData(), row.studentData)
        assertFalse(row.empty)
        assertEquals("Stored", row.name)
    }

    @Test
    fun `an absent profile is not created`() {
        val appDb = openDb()
        appDb.profileDao().add(storedProfile())
        val rowsBefore = profileRowCount(appDb)

        val applied = appDb.editProfile(ABSENT_ID) { it.name = "Ghost" }

        assertFalse(applied)
        assertEquals(rowsBefore, profileRowCount(appDb))
        assertNull(appDb.profileDao().getByIdNow(ABSENT_ID))
    }

    @Test
    fun `an edit that changes the id is refused`() {
        val appDb = openDb()
        appDb.profileDao().add(storedProfile())

        val applied = appDb.editProfile(PROFILE_ID) { it.id = MOVED_ID }

        assertFalse(applied)
        val row = storedRow(appDb, PROFILE_ID)
        assertEquals(7, row.studentNumber)
        assertEquals(storedStudentData(), row.studentData)
        assertFalse(row.empty)
        assertEquals("Stored", row.name)
        assertNull(appDb.profileDao().getByIdNow(MOVED_ID))
    }

    @Test
    fun `the edit runs inside a transaction`() {
        val appDb = openDb()
        appDb.profileDao().add(storedProfile())
        // the probe is only meaningful if it reads false outside an edit
        assertFalse(appDb.inTransaction())
        var inTransaction = false

        appDb.editProfile(PROFILE_ID) { inTransaction = appDb.inTransaction() }

        assertTrue(inTransaction)
    }

    @Test
    fun `the read and the write run inside the transaction`() {
        // The transaction keeps other writers out only between its begin and its commit, so a read
        // taken before it, or a write made after it, leaves a window for another write to be lost.
        // Membership of the edit alone, which the test above pins, cannot see either.
        val appDb = openDb()
        appDb.profileDao().add(storedProfile())
        val dao = spyk(appDb.profileDao())
        val seen = mutableListOf<String>()
        every { dao.getByIdNow(PROFILE_ID) } answers { seen += "read in transaction: ${appDb.inTransaction()}"; callOriginal() }
        every { dao.add(any()) } answers { seen += "write in transaction: ${appDb.inTransaction()}"; callOriginal() }
        val spied = spyk(appDb)
        every { spied.profileDao() } returns dao

        val applied = spied.editProfile(PROFILE_ID) { it.studentNumber = 12 }

        assertTrue(applied)
        assertEquals(listOf("read in transaction: true", "write in transaction: true"), seen)
        assertEquals(12, storedRow(appDb, PROFILE_ID).studentNumber)
    }

    @Test
    fun `a guard that says no writes nothing`() {
        val appDb = openDb()
        appDb.profileDao().add(storedProfile())

        val applied = appDb.editProfile(PROFILE_ID, { false }) { it.studentNumber = 12 }

        assertFalse(applied)
        assertEquals(7, storedRow(appDb, PROFILE_ID).studentNumber)
    }

    @Test
    fun `the guard is shown the row as stored`() {
        val appDb = openDb()
        appDb.profileDao().add(storedProfile())
        appDb.editProfile(PROFILE_ID) { it.name = "Changed" }

        val applied = appDb.editProfile(PROFILE_ID, { it.name == "Changed" }) { it.studentNumber = 12 }

        assertTrue(applied)
        assertEquals(12, storedRow(appDb, PROFILE_ID).studentNumber)
    }

    private fun storedRow(appDb: AppDb, profileId: Int): Profile =
        requireNotNull(appDb.profileDao().getByIdNow(profileId)) { "no profile row $profileId" }

    private fun profileRowCount(appDb: AppDb): Int =
        appDb.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM profiles")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun storedStudentData() = JsonObject().apply {
        addProperty("unitId", 5)
        addProperty("startPointsSemester1", 100)
    }

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

    /** A fresh file per test, so no row leaks between them whatever the sandbox does. */
    private fun openDb(): AppDb {
        context.getDatabasePath(DB_NAME).also { it.parentFile?.mkdirs(); it.delete() }
        return AppDb.builder(context, DB_NAME).build().also { db = it }
    }

    private companion object {
        const val PROFILE_ID = 9001
        const val LOGIN_STORE_ID = 9101
        const val ABSENT_ID = 9002
        const val MOVED_ID = 9003
        const val DB_NAME = "profile-edit-test.db"
    }
}
