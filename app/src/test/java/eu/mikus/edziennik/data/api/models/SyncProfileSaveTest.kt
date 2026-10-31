/*
 * Copyright (c) Mikolaj Olszewski 2026-10-31.
 */
package eu.mikus.edziennik.data.api.models

import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.LuckyNumber
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.removeProfileData
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.spyk
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
 * Pins what a sync task's [Data.saveData] writes to the profile row, and to `App.profile`: only what
 * the task changed on its own copy, onto the row as it is stored at the moment of the write. A task
 * reads its copy of the row when it starts, and a UI save, a LAB edit or another task can write the
 * row while it runs, so writing the copy back whole reverts them.
 *
 * It boots the real `App` and writes the real `App.db`, a process-wide database that outlives each
 * test, so [setUp] asserts the fixture ids are absent and [tearDown] joins every save and removes
 * every row a test can have written, including content rows of a profile whose row is gone.
 *
 * The fixture is the stored row S (profile [P], login store [L]) and `App.profile` loaded from it.
 * A task's copy is read back from the row with `getByIdNow`, as `EdziennikTask.prepare` does, and
 * wrapped in a [DataLibrus], as `Librus` does.
 *
 * Every assertion on one `studentData` value reads it with `asLong` or `asString`, never as the
 * element or its string form, which quotes a string.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncProfileSaveTest {

    private val app: App
        get() = RuntimeEnvironment.getApplication() as App

    private lateinit var previousProfile: Profile
    private var previousLastProfileId = 0
    private val jobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        previousProfile = App.profile
        previousLastProfileId = App.config.lastProfileId
        listOf(P, OTHER).forEach {
            assertNull("profile $it left over from another test", App.db.profileDao().getByIdNow(it))
        }
        assertNull("login store $L left over from another test", App.db.loginStoreDao().getByIdNow(L))
        listOf("luckyNumbers", "endpointTimers").forEach {
            assertEquals("$it rows of profile $P left over from another test", 0, count(it))
        }
        App.db.profileDao().add(storedProfile())
        App.db.loginStoreDao().add(loginStore("stored"))
        app.profileLoad(App.db.profileDao().getByIdNow(P)!!)
    }

    @After
    fun tearDown() {
        try {
            jobs.forEach(::join)
        } finally {
            listOf(P, OTHER).forEach { removeProfileData(App.db, App.config, it, keepProfile = false) }
            // removeProfileData returns early when the row is gone, which is the state `a task for a
            // removed profile writes nothing` leaves behind, so the content rows a test wrote are
            // cleared by id
            App.db.luckyNumberDao().clear(P)
            App.db.endpointTimerDao().clear(P)
            App.db.loginStoreDao().remove(L)
            app.profileLoad(previousProfile)
            App.config.lastProfileId = previousLastProfileId
        }
    }

    @Test
    fun `a UI edit during the task survives and the task's changes land`() {
        val d = startTask()
        join(save(P) { it.name = "Renamed"; it.syncEnabled = false })
        App.db.editProfile(P) { it.studentData.addProperty("marker", "edited") }
        d.unitId = 6
        d.profile!!.studentClassName = "2B"

        d.saveData()

        val saved = row()
        assertEquals(
            "name=Renamed sync=false marker=edited unitId=6 class=2B appName=Renamed appClass=2B",
            "name=${saved.name} sync=${saved.syncEnabled} marker=${key("marker")?.asString} " +
                "unitId=${key("unitId")?.asLong} class=${saved.studentClassName} " +
                "appName=${App.profile.name} appClass=${App.profile.studentClassName}",
        )
    }

    @Test
    fun `a column the task did not change keeps a value saved meanwhile`() {
        val d = startTask()
        App.db.editProfile(P) { it.studentNumber = 13 }
        d.unitId = 6

        d.saveData()

        assertEquals(13, row().studentNumber)
    }

    @Test
    fun `a column the task changed wins over a value saved meanwhile`() {
        val d = startTask()
        App.db.editProfile(P) { it.studentNumber = 13 }
        d.profile!!.studentNumber = 9

        d.saveData()

        assertEquals(9, row().studentNumber)
    }

    @Test
    fun `studentData is merged key by key`() {
        val d = startTask()
        App.db.editProfile(P) {
            it.studentData.addProperty("marker", "edited")
            it.studentData.addProperty("uiKey", "ui")
        }
        d.unitId = 6
        d.profile!!.studentData.addProperty("newKey", "task")
        d.profile.studentData.remove("startPointsSemester1")

        d.saveData()

        assertEquals("edited", key("marker")?.asString)
        assertEquals("ui", key("uiKey")?.asString)
        assertEquals(6L, key("unitId")?.asLong)
        assertEquals("task", key("newKey")?.asString)
        assertFalse(row().studentData.has("startPointsSemester1"))
    }

    @Test
    fun `App profile gets only the task's column changes`() {
        val d = startTask()
        val p = App.profile
        val sd = p.studentData
        val sdBefore = sd.deepCopy()
        App.profile.name = "InMemory"
        d.unitId = 6
        d.profile!!.studentNumber = 9

        d.saveData()

        assertSame(p, App.profile)
        assertEquals(sdBefore, sd)
        assertEquals("InMemory", App.profile.name)
        assertEquals(9, App.profile.studentNumber)
    }

    @Test
    fun `a task for a removed profile writes nothing`() {
        val d = startTask()
        withContent(d)
        d.unitId = 6
        App.db.profileDao().remove(P)

        d.saveData()

        assertNull(App.db.profileDao().getByIdNow(P))
        assertEquals("stored", storeMarker())
        assertEquals(0, count("luckyNumbers"))
        assertEquals(0, count("endpointTimers"))
    }

    @Test
    fun `the baseline is taken when Data is built`() {
        val c = App.db.profileDao().getByIdNow(P)!!
        c.studentClassName = "Before"
        val d = DataLibrus(app, c, LoginStore(L, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject()))
        d.profile!!.studentNumber = 9

        d.saveData()

        assertEquals("1A", row().studentClassName)
        assertEquals(9, row().studentNumber)
    }

    @Test
    fun `a reused id is left to the account that now owns it`() =
        assertReusedIdIsLeftAlone(accountId = 2002, accountLogin = "2002u")

    @Test
    fun `another account by accountId alone is refused`() =
        assertReusedIdIsLeftAlone(accountId = 2002, accountLogin = "1001u")

    @Test
    fun `another account by accountLogin alone is refused`() =
        assertReusedIdIsLeftAlone(accountId = 1001, accountLogin = "2002u")

    @Test
    fun `the same account re-added takes the task's changes`() {
        val d = startTask()
        d.unitId = 6
        replaceRow(1001, "1001u", "New")

        d.saveData()

        assertEquals("New", row().name)
        assertEquals(6L, key("unitId")?.asLong)
    }

    @Test
    fun `a task that changes its own login is not refused`() {
        val d = startTask()
        d.apiLogin = "1001x"
        d.unitId = 6

        d.saveData()

        assertEquals("1001x", key("accountLogin")?.asString)
        assertEquals(6L, key("unitId")?.asLong)
    }

    @Test
    fun `a profile with neither account key saves as before`() {
        App.db.editProfile(P) {
            it.studentData.remove("accountId")
            it.studentData.remove("accountLogin")
        }
        val d = startTask()
        d.unitId = 6

        d.saveData()

        assertEquals(6L, key("unitId")?.asLong)
    }

    @Test
    fun `a second save writes only what changed since the first`() {
        val d = startTask()
        d.profile!!.studentNumber = 9
        d.saveData()
        App.db.editProfile(P) { it.studentNumber = 13 }
        d.unitId = 7

        d.saveData()

        assertEquals(13, row().studentNumber)
        assertEquals(7L, key("unitId")?.asLong)
    }

    @Test
    fun `a change made while a save runs is written by the next save`() {
        // `cancel()` saves on an EventBus thread while an endpoint's response handler may still be
        // changing the profile on another. The guard runs inside the save, after the changes are
        // computed and before the baseline is replaced, so a change made there is that window.
        val spy = spyk(startTask())
        every { spy.isSameAccount(any(), any()) } answers {
            spy.profile!!.studentNumber = 11
            callOriginal()
        }
        spy.unitId = 6

        spy.saveData()
        spy.saveData()

        assertEquals(11, row().studentNumber)
        assertEquals(6L, key("unitId")?.asLong)
    }

    @Test
    fun `a task for another profile leaves App profile alone`() {
        App.db.profileDao().add(otherProfile())
        app.profileLoad(App.db.profileDao().getByIdNow(OTHER)!!)
        val d = startTask()
        d.profile!!.studentNumber = 9

        d.saveData()

        assertEquals(9, row().studentNumber)
        assertEquals(OTHER, App.profile.id)
        assertEquals(3, App.profile.studentNumber)
    }

    /**
     * The shared body of `a reused id is left to the account that now owns it` and the two
     * `another account by ... alone is refused` tests: the task holds content, and before it saves
     * the row is replaced by another account's, which is the shape a reused profile id takes.
     * Neither the row, nor the login store under the same id, nor any content row may be written by
     * the task.
     */
    private fun assertReusedIdIsLeftAlone(accountId: Long, accountLogin: String) {
        val d = startTask()
        withContent(d)
        d.unitId = 6
        replaceRow(accountId, accountLogin, "New")
        val before = Gson().toJson(row())

        d.saveData()

        assertEquals(before, Gson().toJson(row()))
        assertEquals("new", storeMarker())
        assertEquals(0, count("luckyNumbers"))
        assertEquals(0, count("endpointTimers"))
    }

    private fun save(profileId: Int, edit: (Profile) -> Unit): Job =
        app.profileSave(profileId, edit).also { jobs += it }

    private fun join(job: Job) {
        runBlocking { withTimeout(5_000) { job.join() } }
    }

    /** The task's copy and its [DataLibrus], as `EdziennikTask.prepare` and `Librus` build them. */
    private fun startTask(): DataLibrus =
        App.db.profileDao().getByIdNow(P)!!.let {
            DataLibrus(app, it, LoginStore(L, LoginType.LIBRUS, LoginMode.LIBRUS_EMAIL, JsonObject()))
        }

    private fun row(): Profile =
        requireNotNull(App.db.profileDao().getByIdNow(P)) { "no profile row $P" }

    private fun key(name: String): JsonElement? = row().studentData.get(name)

    private fun storeMarker(): String? =
        App.db.loginStoreDao().getByIdNow(L)?.data?.get("marker")?.asString

    private fun count(table: String): Int =
        App.db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $table WHERE profileId = $P")).use {
            it.moveToFirst()
            it.getInt(0)
        }

    /** One timer and one lucky number for [P], which a task writes after the profile row. */
    private fun withContent(data: DataLibrus) {
        data.setSyncNext(9999, 1L)
        data.luckyNumberList += LuckyNumber(P, Date(2026, 10, 30), 13)
    }

    /**
     * Deletes the row and inserts another for the same ids, as a removed profile's id is reused by
     * the next account added. A `null` argument leaves its key out.
     */
    private fun replaceRow(accountId: Long?, accountLogin: String?, name: String) {
        App.db.profileDao().remove(P)
        val studentData = JsonObject().apply {
            accountId?.let { addProperty("accountId", it) }
            accountLogin?.let { addProperty("accountLogin", it) }
            addProperty("unitId", 8)
        }
        App.db.profileDao().add(
            Profile(
                id = P,
                loginStoreId = L,
                loginStoreType = LoginType.LIBRUS,
                name = name,
                studentData = studentData,
            )
        )
        App.db.loginStoreDao().add(loginStore("new"))
    }

    private fun loginStore(marker: String) = LoginStore(
        L,
        LoginType.LIBRUS,
        LoginMode.LIBRUS_EMAIL,
        JsonObject().apply { addProperty("marker", marker) },
    )

    private fun storedProfile() = Profile(
        id = P,
        loginStoreId = L,
        loginStoreType = LoginType.LIBRUS,
        name = "Stored",
        studentData = JsonObject().apply {
            addProperty("unitId", 5)
            addProperty("startPointsSemester1", 100)
            addProperty("marker", "stored")
            addProperty("accountId", 1001)
            addProperty("accountLogin", "1001u")
        },
    ).apply {
        empty = false
        syncEnabled = true
        studentNumber = 7
        studentClassName = "1A"
    }

    private fun otherProfile() = Profile(
        id = OTHER,
        loginStoreId = OTHER_LOGIN_STORE_ID,
        loginStoreType = LoginType.LIBRUS,
        name = "Other",
        studentData = JsonObject().apply { addProperty("k", 1) },
    ).apply {
        studentNumber = 3
    }

    private companion object {
        const val P = 9031
        const val L = 9131
        const val OTHER = 9032
        const val OTHER_LOGIN_STORE_ID = 9132
    }
}
