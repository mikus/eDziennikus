/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import eu.mikus.edziennik.data.db.dao.AttendanceDaoSelective
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins what `Attendance.dateUnknown` does in storage: Room persists a set flag, and the selective
 * update, which a sync applies to a row it sees again, rewrites the column.
 *
 * These are characterisation tests, not red-first. The column and `Attendance.dateUnknown` already
 * existed when they were written, so both passed on arrival. What shows they are not vacuous is a
 * mutation: adding `attendanceDateUnknown` to `AttendanceDao`'s `skippedColumns` turns the second
 * one red.
 *
 * The second is the only guard that the flag is bound on every sync. `skippedColumns` is an
 * unvalidated list of strings (it already names `announcementText`, which is not a column on
 * `attendances`), so reading the annotation proves nothing about which columns the generated
 * update writes. A sync that updates an entity built without writing the flag clears a stored
 * `true`, which is why the parser writes `dateUnknown` on every path, `false` included.
 *
 * Both tests read the raw column, and the first reads the entity back through Room as well.
 * `Attendance.dateUnknown` initialises to false, so a false read off an entity says nothing about
 * what SQLite holds.
 *
 * It drives `AttendanceDaoSelective` directly: `AttendanceDao.update` binds
 * `AttendanceDaoSelective(App.db)`, a lateinit global this test never initialises.
 *
 * It needs a real `Context` and a real SQLite file, hence Robolectric (JUnit 4, via Vintage).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AttendanceDateUnknownTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private var db: AppDb? = null

    @After
    fun closeDb() {
        db?.close()
    }

    @Test
    fun `an entity written with the flag set round-trips`() {
        val appDb = openDb()
        appDb.attendanceDao().add(newAttendance(id = 500L).also { it.dateUnknown = true })

        assertEquals(1, storedDateUnknown(appDb, 500L))
        assertTrue(appDb.attendanceDao().getByIdNow(PROFILE_ID, 500L)!!.dateUnknown)
    }

    @Test
    fun `the selective update clears a stored unknown flag`() {
        val appDb = openDb()
        appDb.attendanceDao().add(newAttendance(id = 501L).also { it.dateUnknown = true })
        // the premise: the flag really is stored before the update runs
        assertEquals(1, storedDateUnknown(appDb, 501L))

        AttendanceDaoSelective(appDb).update(newAttendance(id = 501L))

        assertEquals(0, storedDateUnknown(appDb, 501L))
    }

    /**
     * Reads the raw `attendanceDateUnknown` column. A missing row fails here, so the value
     * returned is a stored one, never an absent row.
     */
    private fun storedDateUnknown(appDb: AppDb, attendanceId: Long): Int =
        appDb.openHelper.readableDatabase
            .query(
                "SELECT attendanceDateUnknown FROM attendances " +
                    "WHERE profileId = $PROFILE_ID AND attendanceId = $attendanceId"
            )
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

    private fun newAttendance(id: Long) = Attendance(
        profileId = PROFILE_ID,
        id = id,
        baseType = Attendance.TYPE_ABSENT,
        typeName = "nieobecność",
        typeShort = "nb",
        typeSymbol = "nb",
        typeColor = null,
        date = Date(2026, 6, 20),
        startTime = Time(8, 0, 0),
        semester = 1,
        teacherId = 0L,
        subjectId = 0L,
        addedDate = 0L,
    )

    /** A fresh file per test, so no row leaks between them whatever the sandbox does. */
    private fun openDb(): AppDb {
        context.getDatabasePath(DB_NAME).also { it.parentFile?.mkdirs(); it.delete() }
        return AppDb.builder(context, DB_NAME).build().also { db = it }
    }

    private companion object {
        const val PROFILE_ID = 1
        const val DB_NAME = "attendance-date-unknown-test.db"
    }
}
