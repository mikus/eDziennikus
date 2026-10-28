/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins that an attendance row whose start time is unknown can be read back. `attendanceTime` is a
 * nullable column and [Attendance.startTime] is `Time?`, but `AttendanceFull` - the type Room builds
 * for the `@RawQuery` reads - once declared that constructor parameter non-null, so the generated
 * DAO threw on the first such row and one row hid every other attendance of the profile.
 *
 * Every test writes a NULL and asserts it in the raw column before reading it back. Before the fix
 * that assertion is what shows the NULL reached storage, so the NullPointerException comes from the
 * read and not from the write. In the third test it is also the only assertion that the row with no
 * start time really holds a NULL.
 *
 * It drives [eu.mikus.edziennik.data.db.dao.AttendanceDao.add], `getAllNow` and `getByIdNow` only.
 * `update` binds `AttendanceDaoSelective(App.db)`, a lateinit global this test never initialises.
 *
 * It needs a real `Context` and a real SQLite file, hence Robolectric (JUnit 4, via Vintage).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AttendanceNullTimeTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private var db: AppDb? = null

    @After
    fun closeDb() {
        db?.close()
    }

    @Test
    fun `a row with no start time reads back through the list query`() {
        val appDb = openDb()
        appDb.attendanceDao().add(newAttendance(id = 100L, startTime = null))
        assertNull(storedAttendanceTime(appDb, 100L))

        val rows = appDb.attendanceDao().getAllNow(PROFILE_ID)

        assertEquals(1, rows.size)
        assertNull(rows.single().startTime)
    }

    @Test
    fun `a row with no start time reads back through the single-row query`() {
        val appDb = openDb()
        appDb.attendanceDao().add(newAttendance(id = 101L, startTime = null))
        assertNull(storedAttendanceTime(appDb, 101L))

        val row = appDb.attendanceDao().getByIdNow(PROFILE_ID, 101L)

        assertNotNull(row)
        assertNull(row!!.startTime)
    }

    @Test
    fun `one row with no start time does not hide the rows that have one`() {
        val appDb = openDb()
        appDb.attendanceDao().add(newAttendance(id = 102L, startTime = Time(8, 0, 0)))
        appDb.attendanceDao().add(newAttendance(id = 103L, startTime = null))
        assertNull(storedAttendanceTime(appDb, 103L))

        val rows = appDb.attendanceDao().getAllNow(PROFILE_ID)

        assertEquals(2, rows.size)
        assertEquals(setOf(102L, 103L), rows.map { it.id }.toSet())
        // the row that has a time still reads it back: a read that returned every start time as
        // null would pass the assertions above
        assertEquals(Time(8, 0, 0).value, rows.single { it.id == 102L }.startTime?.value)
    }

    /**
     * Reads the raw `attendanceTime` column, NULL as null. A missing row fails here, so a null
     * return is a stored NULL, never an absent row.
     */
    private fun storedAttendanceTime(appDb: AppDb, attendanceId: Long): String? =
        appDb.openHelper.readableDatabase
            .query(
                "SELECT attendanceTime FROM attendances " +
                    "WHERE profileId = $PROFILE_ID AND attendanceId = $attendanceId"
            )
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                if (cursor.isNull(0)) null else cursor.getString(0)
            }

    private fun newAttendance(id: Long, startTime: Time?) = Attendance(
        profileId = PROFILE_ID,
        id = id,
        baseType = Attendance.TYPE_ABSENT,
        typeName = "nieobecność",
        typeShort = "nb",
        typeSymbol = "nb",
        typeColor = null,
        date = Date(2026, 6, 20),
        startTime = startTime,
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
        const val DB_NAME = "attendance-null-time-test.db"
    }
}
