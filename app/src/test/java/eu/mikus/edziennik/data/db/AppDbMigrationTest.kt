/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser
import eu.mikus.edziennik.data.db.dao.EventDaoSelective
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.utils.models.Date
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The project's first Room migration. `AppDb` used to be `version = 1` with only
 * `fallbackToDestructiveMigration()`, so any version bump dropped every table silently.
 *
 * The v1 database is rebuilt from the committed `1.json`, so the fixture cannot drift from the
 * schema Room validates against, and it is opened through [AppDb.builder] — the same chain
 * production uses — so these tests prove production has the migration, not that the test does.
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

        val appDb = openV2(context, DB_NAME).also { db = it }
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
    fun `the database reports version 2 after the migration`() {
        createV1Database(context, DB_NAME)

        val version = openV2(context, DB_NAME).also { db = it }.openHelper.readableDatabase.version

        assertEquals(2, version)
    }

    @Test
    fun `an entity written with the flag set round-trips`() {
        createV1Database(context, DB_NAME)
        val appDb = openV2(context, DB_NAME).also { db = it }

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
     * Robolectric + in-memory AppDb setup for one assertion.
     *
     * Drives EventDaoSelective directly: EventDao.update binds EventDaoSelective(App.db), a
     * lateinit global this test never initialises.
     */
    @Test
    fun `the selective update clears a stored unknown flag`() {
        createV1Database(context, DB_NAME)
        val appDb = openV2(context, DB_NAME).also { db = it }
        appDb.eventDao().add(newEvent(profileId = 1, id = 300L).also { it.dateUnknown = true })

        EventDaoSelective(appDb).update(newEvent(profileId = 1, id = 300L))

        // read the column, not the entity: Event.dateUnknown initialises to false, so an
        // entity-level assertion would pass whatever SQLite holds
        val stored = appDb.openHelper.readableDatabase
            .query("SELECT eventDateUnknown FROM events WHERE profileId = 1 AND eventId = 300")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        assertEquals(0, stored)
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

    private fun createV1Database(ctx: Context, name: String) {
        val root = JsonParser.parseString(
            File("schemas/eu.mikus.edziennik.data.db.AppDb/1.json").readText()
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
        raw.execSQL("PRAGMA user_version = 1")
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

    private fun openV2(ctx: Context, name: String): AppDb = AppDb.builder(ctx, name).build()

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
