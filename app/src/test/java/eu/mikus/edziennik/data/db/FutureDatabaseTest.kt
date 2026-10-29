/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser
import eu.mikus.edziennik.data.db.entity.Event
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What `AppDb` does with a database file whose stored `user_version` is higher than the build's:
 * a file written by a newer build, then opened by an older one (a sideloaded downgrade).
 *
 * `AppDb.builder` ends in `fallbackToDestructiveMigration()`. In Room 2.4.3 a downgrade is routed
 * through `onUpgrade`, and `isMigrationRequired` answers false for it, so the destructive fallback
 * fires and Room drops every table and recreates them empty. The build stays green and the user
 * silently loses their local diary.
 *
 * The test below asserts that **current, broken** behaviour on purpose: it is the pin on what
 * Room 2.4.3 does today, kept so a later change that is meant to stop the wipe has something to
 * be measured against.
 *
 * The fixture is rebuilt from the committed `<version>.json` and opened through [AppDb.builder] -
 * the same chain production uses - for the reasons given on `AppDbMigrationTest`, whose harness
 * this adapts. It needs a real `Context` and a real SQLite file, hence Robolectric (JUnit 4, via
 * Vintage). Unit tests run with the working directory set to `app/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FutureDatabaseTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private var db: AppDb? = null

    @After
    fun closeDb() {
        db?.close()
    }

    @Test
    fun `today, a database from the future is silently emptied by the destructive fallback`() {
        createCurrentDatabase(context, DB_NAME)
        insertEvent(context, DB_NAME, profileId = 1, eventId = 100L)
        insertProfile(context, DB_NAME, profileId = 1)
        // the rows are in the file before Room opens it, so the emptiness below is a drop, not an
        // insert that never landed
        assertEquals(1, countRows(context, DB_NAME, "events"))
        assertEquals(1, countRows(context, DB_NAME, "profiles"))
        setUserVersion(context, DB_NAME, APP_DB_VERSION_TODAY + 1)

        val appDb = AppDb.builder(context, DB_NAME).build().also { db = it }

        // onDowngrade -> onUpgrade -> dropAllTables + createAllTables
        assertNull(appDb.eventDao().getByIdNow(1, 100L))
        assertTrue(appDb.profileDao().allNow.isEmpty())
        // the file was rebuilt at the build's own version, not left at the future one
        assertEquals(APP_DB_VERSION_TODAY, appDb.openHelper.readableDatabase.version)
    }

    /** Rebuilds the schema committed as `AppDb`'s current version, `user_version` set to match. */
    private fun createCurrentDatabase(ctx: Context, name: String) =
        createDatabase(ctx, name, APP_DB_VERSION_TODAY)

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

    private fun setUserVersion(ctx: Context, name: String, version: Int) =
        withRawDatabase(ctx, name) { it.execSQL("PRAGMA user_version = $version") }

    private fun countRows(ctx: Context, name: String, table: String): Int =
        withRawDatabase(ctx, name) { raw ->
            raw.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }

    /** Inserts with the full v3 column list, `eventDateUnknown` included. */
    private fun insertEvent(ctx: Context, name: String, profileId: Int, eventId: Long) =
        withRawDatabase(ctx, name) { raw ->
            raw.execSQL(
                """INSERT INTO events (profileId, eventId, eventDate, eventTime, eventTopic,
                    eventColor, eventType, teacherId, subjectId, teamId, addedDate,
                    eventAddedManually, eventBlacklisted, eventIsDone, eventDateUnknown,
                    eventIsDownloaded, homeworkBody, attachmentIds, attachmentNames, keep)
                    VALUES ($profileId, $eventId, '2026-06-20', NULL, 'zadanie z polskiego', NULL,
                    ${Event.TYPE_HOMEWORK}, 0, 0, 0, 0, 0, 0, 0, 0, 1, NULL, NULL, NULL, 0)"""
            )
        }

    /**
     * A second table to drop, so the emptiness assertion does not rest on `events` alone. Inserts
     * with the full v3 column list.
     */
    private fun insertProfile(ctx: Context, name: String, profileId: Int) =
        withRawDatabase(ctx, name) { raw ->
            raw.execSQL(
                """INSERT INTO profiles (profileId, loginStoreId, loginStoreType, name, subname,
                    studentNameLong, studentNameShort, accountName, studentData, image, empty,
                    archived, syncEnabled, userCode, archiveId, studentNumber, studentClassName,
                    studentSchoolYearStart, dateSemester1Start, dateSemester2Start, dateYearEnd,
                    disabledNotifications, lastReceiversSync)
                    VALUES ($profileId, 1, 1, 'Jan', NULL, 'Jan Kowalski', 'Jan K.', NULL, '{}',
                    NULL, 0, 0, 1, 'kod', NULL, 1, NULL, 2025, '2025-09-01', '2026-02-01',
                    '2026-06-26', NULL, 0)"""
            )
        }

    private fun <T> withRawDatabase(ctx: Context, name: String, block: (SQLiteDatabase) -> T): T {
        val raw = SQLiteDatabase.openDatabase(
            ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE,
        )
        return try {
            block(raw)
        } finally {
            raw.close()
        }
    }

    private companion object {
        const val DB_NAME = "future-database-test.db"

        /** `AppDb`'s `version` today. Replaced by the production constant once there is one. */
        const val APP_DB_VERSION_TODAY = 3
    }
}
