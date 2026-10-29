/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser
import eu.mikus.edziennik.data.db.dao.EventDaoSelective
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The project's Room migrations. `AppDb` used to be `version = 1` with only
 * `fallbackToDestructiveMigration()`, so any version bump dropped every table silently.
 *
 * Each starting database is rebuilt from the committed `<version>.json`, so a fixture cannot drift
 * from the schema Room validates against, and it is opened through [AppDb.builder] — the same chain
 * production uses — so these tests prove production has the migrations, not that the test does.
 *
 * Not every test here is a migration test: the flag's write round trip and the selective updates
 * that clear it and a stored time share this file because they need the same Robolectric + AppDb
 * setup.
 *
 * It needs a real `Context` and a real SQLite file, hence Robolectric (JUnit 4, via Vintage).
 * Unit tests run with the working directory set to `app/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppDbMigrationTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private var db: AppDb? = null

    @After
    fun closeDb() {
        db?.close()
    }

    @Test
    fun `a v1 row survives the migration and reads a false flag`() {
        createV1Database(context, DB_NAME)
        insertV1Event(context, DB_NAME, profileId = 1, eventId = 100L)

        val appDb = openCurrent(context, DB_NAME).also { db = it }
        val row = appDb.eventDao().getByIdNow(1, 100L)

        // if the destructive fallback fired instead of the migration, the row is gone
        assertNotNull(row)
        assertEquals("zadanie z polskiego", row!!.topic)
        assertEquals("2026-06-20", row.date.stringY_m_d)
        // what SQLite holds for the migrated row. The entity assertion below cannot stand alone:
        // Event.dateUnknown initialises to false, and Room's raw-query mapping skips a column
        // missing from the cursor, so it would pass whatever the stored value is.
        val stored = appDb.openHelper.readableDatabase
            .query("SELECT eventDateUnknown FROM events WHERE profileId = 1 AND eventId = 100")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        assertEquals(0, stored)
        assertFalse(row.dateUnknown)
    }

    @Test
    fun `the database reports version 3 after the migration`() {
        createV1Database(context, DB_NAME)

        val version = openCurrent(context, DB_NAME).also { db = it }.openHelper.readableDatabase.version

        assertEquals(3, version)
    }

    @Test
    fun `a v1 attendance survives the chain to v3 and reads a false flag`() {
        createV1Database(context, DB_NAME)
        insertV1Attendance(context, DB_NAME, profileId = 1, attendanceId = 400L)

        val appDb = openCurrent(context, DB_NAME).also { db = it }
        val row = appDb.attendanceDao().getByIdNow(1, 400L)

        // a v1 database has to chain 1->2->3; if the destructive fallback fired instead of the
        // migrations, the row is gone
        assertNotNull(row)
        assertEquals("2026-06-20", row!!.date.stringY_m_d)
        assertEquals(0, storedAttendanceDateUnknown(appDb, 400L))
        assertFalse(row.dateUnknown)
    }

    @Test
    fun `a v2 attendance reaches v3 with the column defaulted`() {
        createV2Database(context, DB_NAME)
        insertV1Attendance(context, DB_NAME, profileId = 1, attendanceId = 401L)

        val appDb = openCurrent(context, DB_NAME).also { db = it }
        val row = appDb.attendanceDao().getByIdNow(1, 401L)

        // starts at version 2, so MIGRATION_1_2 is never visited: this is the one test that
        // isolates MIGRATION_2_3
        assertNotNull(row)
        assertEquals("2026-06-20", row!!.date.stringY_m_d)
        assertEquals(0, storedAttendanceDateUnknown(appDb, 401L))
        assertFalse(row.dateUnknown)
    }

    @Test
    fun `an entity written with the flag set round-trips`() {
        createV1Database(context, DB_NAME)
        val appDb = openCurrent(context, DB_NAME).also { db = it }

        appDb.eventDao().add(newEvent(profileId = 1, id = 200L).also { it.dateUnknown = true })

        // Room's cursor path builds EventFull through its 11-argument primary constructor, so this
        // read does not exercise the copy in the (event, metadata) constructor...
        assertTrue(appDb.eventDao().getByIdNow(1, 200L)!!.dateUnknown)

        // ...which is the one Event.withMetadata goes through. Omitting its copy line would leave
        // every other assertion green.
        val metadata = Metadata(1, MetadataType.EVENT, 200L, false, false)
        assertTrue(newEvent(profileId = 1, id = 200L).also { it.dateUnknown = true }
            .withMetadata(metadata).dateUnknown)
    }

    /**
     * Not a migration test. It lives here because this file already hosts a non-migration round
     * trip (`an entity written with the flag set round-trips`), and splitting would duplicate the
     * Robolectric + AppDb setup for one assertion.
     *
     * Drives EventDaoSelective directly: EventDao.update binds EventDaoSelective(App.db), a
     * lateinit global this test never initialises.
     */
    @Test
    fun `the selective update clears a stored unknown flag`() {
        createV1Database(context, DB_NAME)
        val appDb = openCurrent(context, DB_NAME).also { db = it }
        appDb.eventDao().add(newEvent(profileId = 1, id = 300L).also { it.dateUnknown = true })
        // the flag is set before the update, so the 0 below is a cleared 1, not a column that
        // never held one
        assertEquals(1, storedDateUnknown(appDb, 300L))

        EventDaoSelective(appDb).update(newEvent(profileId = 1, id = 300L))

        assertEquals(0, storedDateUnknown(appDb, 300L))
    }

    /**
     * Not a migration test, and it drives EventDaoSelective directly - both for the reasons given
     * on the test above.
     *
     * Pins one DAO fact: a selective update whose event has a null `time` clears a stored "000000"
     * to NULL, because `eventTime` is not among the selective update's `skippedColumns`. "000000"
     * is what an absent or unreadable TimeFrom with no lesson range used to be filed as.
     *
     * What such a row holds after later syncs is not pinned here. A sync that saves the event from
     * the HomeWorks endpoint again writes over it the time `LibrusApiEvents` read for it then,
     * through `librusEventTime`: NULL only while the lesson-range lookup finds no range and
     * TimeFrom is absent or unreadable; otherwise the range's start or the TimeFrom read, which is
     * "000000" again for a TimeFrom of "00:00" or "00:00:00". An event the school stops sending is
     * not rewritten by that endpoint, but need not keep "000000" either: a future one can still be
     * deleted by a prune, such as that endpoint's own `futureExceptTypes` for a type other than
     * `TYPE_HOMEWORK` and `TYPE_PT_MEETING`, or another endpoint's `futureWithType(TYPE_HOMEWORK)`
     * for one stored with no category id, as `TYPE_HOMEWORK`.
     */
    @Test
    fun `the selective update clears a stored midnight time`() {
        createV1Database(context, DB_NAME)
        val appDb = openCurrent(context, DB_NAME).also { db = it }
        appDb.eventDao().add(newEvent(profileId = 1, id = 301L).also { it.time = Time(0, 0, 0) })
        // the time is stored before the update, so the NULL below is a cleared value, not a
        // column that never held one
        assertEquals("000000", storedEventTime(appDb, 301L))

        EventDaoSelective(appDb).update(newEvent(profileId = 1, id = 301L))

        assertNull(storedEventTime(appDb, 301L))
    }

    /**
     * Reads the raw `eventTime` column, NULL as null. A missing row fails here, so a null return
     * is a stored NULL, never an absent row.
     */
    private fun storedEventTime(appDb: AppDb, eventId: Long): String? =
        appDb.openHelper.readableDatabase
            .query("SELECT eventTime FROM events WHERE profileId = 1 AND eventId = $eventId")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                if (cursor.isNull(0)) null else cursor.getString(0)
            }

    /**
     * Reads the column, not the entity: Event.dateUnknown initialises to false, and Room's
     * raw-query mapping skips a column missing from the cursor, so an entity-level assertion
     * would pass whatever SQLite holds. Both halves are load-bearing - the default alone does
     * not make an entity read unsafe, as `an entity written with the flag set round-trips`
     * in this same file shows. A missing row fails here.
     */
    private fun storedDateUnknown(appDb: AppDb, eventId: Long): Int =
        appDb.openHelper.readableDatabase
            .query("SELECT eventDateUnknown FROM events WHERE profileId = 1 AND eventId = $eventId")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

    /** Reads the raw column rather than the entity, for the reason given on [storedDateUnknown]. */
    private fun storedAttendanceDateUnknown(appDb: AppDb, attendanceId: Long): Int =
        appDb.openHelper.readableDatabase
            .query(
                "SELECT attendanceDateUnknown FROM attendances " +
                    "WHERE profileId = 1 AND attendanceId = $attendanceId"
            )
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

    private fun newEvent(profileId: Int, id: Long) = Event(
        profileId = profileId,
        id = id,
        date = Date(2026, 6, 20),
        time = null,
        topic = "zadanie z polskiego",
        color = null,
        type = Event.TYPE_HOMEWORK,
        teacherId = 0L,
        subjectId = 0L,
        teamId = 0L,
    )

    private fun createV1Database(ctx: Context, name: String) = createDatabase(ctx, name, version = 1)

    private fun createV2Database(ctx: Context, name: String) = createDatabase(ctx, name, version = 2)

    /**
     * Rebuilds the schema committed as `<version>.json`. Room only ever rewrites the file of the
     * version `AppDb` currently declares, so every earlier one is a frozen fixture it will not
     * regenerate: restore one from git rather than letting a build recreate it.
     */
    private fun createDatabase(ctx: Context, name: String, version: Int) {
        val root = JsonParser.parseString(
            File("schemas/eu.mikus.edziennik.data.db.AppDb/$version.json").readText()
        ).asJsonObject.getAsJsonObject("database")
        val file = ctx.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        root.getAsJsonArray("entities").forEach { e ->
            val o = e.asJsonObject
            val table = o.get("tableName").asString
            raw.execSQL(o.get("createSql").asString.replace("\${TABLE_NAME}", table))
            o.getAsJsonArray("indices")?.forEach {
                raw.execSQL(it.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
            }
        }
        root.getAsJsonArray("setupQueries").forEach { raw.execSQL(it.asString) }
        raw.execSQL("PRAGMA user_version = $version")
        raw.close()
    }

    /** Inserts with the v1 column list only: there is no `eventDateUnknown` yet. */
    private fun insertV1Event(ctx: Context, name: String, profileId: Int, eventId: Long) {
        val raw = SQLiteDatabase.openDatabase(
            ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE,
        )
        raw.execSQL(
            """INSERT INTO events (profileId, eventId, eventDate, eventTime, eventTopic, eventColor,
                eventType, teacherId, subjectId, teamId, addedDate, eventAddedManually,
                eventBlacklisted, eventIsDone, eventIsDownloaded, keep)
                VALUES ($profileId, $eventId, '2026-06-20', NULL, 'zadanie z polskiego', NULL,
                ${Event.TYPE_HOMEWORK}, 0, 0, 0, 0, 0, 0, 0, 1, 0)"""
        )
        raw.close()
    }

    /**
     * Inserts with the v1 column list only: there is no `attendanceDateUnknown` yet. `attendances`
     * is byte-identical in `1.json` and `2.json`, so one insert serves both fixtures.
     */
    private fun insertV1Attendance(ctx: Context, name: String, profileId: Int, attendanceId: Long) {
        val raw = SQLiteDatabase.openDatabase(
            ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE,
        )
        raw.execSQL(
            """INSERT INTO attendances (profileId, attendanceId, attendanceBaseType,
                attendanceTypeName, attendanceTypeShort, attendanceTypeSymbol, attendanceTypeColor,
                attendanceDate, attendanceTime, attendanceSemester, teacherId, subjectId,
                addedDate, attendanceLessonTopic, attendanceLessonNumber, attendanceIsCounted, keep)
                VALUES ($profileId, $attendanceId, ${Attendance.TYPE_ABSENT}, 'nieobecność', 'nb',
                'nb', NULL, '2026-06-20', '080000', 1, 0, 0, 0, NULL, NULL, 1, 0)"""
        )
        raw.close()
    }

    private fun openCurrent(ctx: Context, name: String): AppDb = AppDb.builder(ctx, name).build()

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
