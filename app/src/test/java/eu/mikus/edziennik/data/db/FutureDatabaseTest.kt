/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser
import eu.mikus.edziennik.data.db.entity.Event
import java.io.File
import java.nio.ByteBuffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * The first test calls `AppDb.builder` directly, without the guard, and asserts that wipe on
 * purpose: it pins what Room does to a file it is handed, which is why the guard exists. Every
 * other test drives [AppDb.open], the function `AppDb(context)` builds the production database
 * through, and asserts what happens instead: the file is left alone and a sidecar is opened.
 * `AppDb(context)` itself cannot be driven from here: it hard-codes the real name and caches a
 * singleton.
 *
 * The byte-identity tests hold the guard to what it may do to the file it leaves alone. The `.db`
 * is never changed, an existing `-wal` is never changed, and nothing is deleted or renamed. A
 * `-shm`, and a zero-length `-wal`, may appear where there were none: SQLite makes both on any
 * open of a write-ahead-log database, read-only included, and Room's own open makes the same two.
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
    private val opened = mutableListOf<AppDb>()

    @Before
    fun clearChoice() {
        // a value left by an earlier test would let "open recorded it" pass without open
        AppDb.choice = null
    }

    @After
    fun closeDatabases() {
        opened.forEach { it.close() }
        AppDb.choice = null
    }

    @Test
    fun `today, a database from the future is silently emptied by the destructive fallback`() {
        createCurrentDatabase(context, DB_NAME)
        insertEvent(context, DB_NAME, profileId = 1, eventId = 100L)
        insertProfile(context, DB_NAME, profileId = 1)
        // the rows are in the file before Room opens it, so the emptiness below is a drop, not an
        // insert that never landed
        assertEquals(
            mapOf("events" to 1, "profiles" to 1),
            mapOf(
                "events" to countRows(context, DB_NAME, "events"),
                "profiles" to countRows(context, DB_NAME, "profiles"),
            ),
        )
        setUserVersion(context, DB_NAME, APP_DB_VERSION + 1)

        val appDb = AppDb.builder(context, DB_NAME).build().also { opened += it }
        // Room opens the file lazily, and opening it is what drops the tables: open it before
        // looking. onDowngrade -> onUpgrade -> dropAllTables + createAllTables
        val version = appDb.openHelper.readableDatabase.version

        // One assertion over all three facts, so that a failure reports each of them and not just
        // the first: both tables are emptied, and the file is rebuilt at the build's own version,
        // not left at the future one. Rows are counted in the file and not read through a DAO,
        // because a surviving profile row cannot be turned into a `Profile` (its `loginStoreType`
        // is not one the converter knows), so the read would throw instead of reporting.
        assertEquals(
            mapOf("events" to 0, "profiles" to 0, "version" to APP_DB_VERSION),
            mapOf(
                "events" to countRows(context, DB_NAME, "events"),
                "profiles" to countRows(context, DB_NAME, "profiles"),
                "version" to version,
            ),
        )
    }

    @Test
    fun `a guarded open of a database from the future records Newer on AppDb choice`() {
        plantFutureDatabase()

        openGuarded()

        assertEquals(DatabaseChoice.Newer(SIDECAR_NAME, FUTURE_VERSION), AppDb.choice)
    }

    @Test
    fun `a guarded open of a database from the future builds on the sidecar`() {
        plantFutureDatabase()

        val appDb = openGuarded()

        assertEquals(SIDECAR_NAME, appDb.openHelper.databaseName)
    }

    @Test
    fun `a guarded open of a database from the future yields an empty sidecar`() {
        plantFutureDatabase()

        openGuarded()

        // counted in the sidecar file itself: an unguarded open would leave the real file empty
        // too, but it would not leave a sidecar
        assertEquals(
            mapOf("events" to 0, "profiles" to 0),
            mapOf(
                "events" to countRows(context, SIDECAR_NAME, "events"),
                "profiles" to countRows(context, SIDECAR_NAME, "profiles"),
            ),
        )
    }

    @Test
    fun `a guarded open of a database from the future leaves the main file byte-identical`() {
        plantFutureDatabase()
        val before = familySnapshot()

        openGuarded()

        assertSameBytes(DB_NAME, before, familySnapshot())
    }

    @Test
    fun `a guarded open of a clean write-ahead-log database from the future leaves the main file byte-identical`() {
        plantCleanWalFutureDatabase()
        val before = familySnapshot()

        openGuarded()

        assertSameBytes(DB_NAME, before, familySnapshot())
    }

    @Test
    fun `a guarded open of a clean write-ahead-log database from the future adds nothing but a -shm and a -wal`() {
        plantCleanWalFutureDatabase()
        val before = familySnapshot()

        openGuarded()

        assertEquals(emptySet<String>(), addedFiles(before, familySnapshot()) - SHM_AND_WAL)
    }

    @Test
    fun `a guarded open of a clean write-ahead-log database from the future adds only an empty -wal`() {
        plantCleanWalFutureDatabase()

        openGuarded()

        // no -wal before, so whatever is there now was made by the open, and must be zero-length
        assertEquals(0, familySnapshot()[WAL_NAME]?.size ?: 0)
    }

    @Test
    fun `a guarded open of a database whose newer version is only in the log records Newer`() {
        plantHotWalFutureDatabase()

        openGuarded()

        assertEquals(DatabaseChoice.Newer(SIDECAR_NAME, FUTURE_VERSION), AppDb.choice)
    }

    @Test
    fun `a guarded open of a database whose newer version is only in the log leaves the main file byte-identical`() {
        plantHotWalFutureDatabase()
        val before = familySnapshot()

        openGuarded()

        assertSameBytes(DB_NAME, before, familySnapshot())
    }

    @Test
    fun `a guarded open of a database whose newer version is only in the log leaves its -wal byte-identical`() {
        plantHotWalFutureDatabase()
        val before = familySnapshot()

        openGuarded()

        assertSameBytes(WAL_NAME, before, familySnapshot())
    }

    @Test
    fun `a guarded open of a database whose newer version is only in the log deletes and renames nothing`() {
        plantHotWalFutureDatabase()
        val before = familySnapshot()

        openGuarded()

        assertEquals(emptySet<String>(), before.keys - familySnapshot().keys)
    }

    @Test
    fun `a guarded open of a database whose newer version is only in the log adds nothing but a -shm`() {
        plantHotWalFutureDatabase()
        val before = familySnapshot()

        openGuarded()

        assertEquals(emptySet<String>(), addedFiles(before, familySnapshot()) - SHM_AND_WAL)
    }

    @Test
    fun `a guarded open of an unreadable database records Unreadable on AppDb choice`() {
        plantUnreadableDatabase()

        openGuarded()

        assertEquals(DatabaseChoice.Unreadable(SIDECAR_NAME), AppDb.choice)
    }

    @Test
    fun `a guarded open of an unreadable database leaves it byte-identical`() {
        plantUnreadableDatabase()
        val before = familySnapshot()

        openGuarded()

        assertSameBytes(DB_NAME, before, familySnapshot())
    }

    @Test
    fun `the sidecar is reused on a second guarded open`() {
        plantFutureDatabase()
        openGuarded().close()
        // what the app itself wrote to the sidecar while parked
        insertEvent(context, SIDECAR_NAME, profileId = 1, eventId = 200L)

        val second = openGuarded()

        assertNotNull(second.eventDao().getByIdNow(1, 200L))
    }

    @Test
    fun `a guarded open looks for the database where Room will open it`() {
        plantFutureDatabase()

        openGuarded(from = contextWithAnotherDatabaseDirectory())

        assertEquals(DatabaseChoice.Newer(SIDECAR_NAME, FUTURE_VERSION), AppDb.choice)
    }

    @Test
    fun `a guarded open through a context with another database directory leaves the main file byte-identical`() {
        plantFutureDatabase()
        val before = familySnapshot()

        openGuarded(from = contextWithAnotherDatabaseDirectory())

        assertSameBytes(DB_NAME, before, familySnapshot())
    }

    @Test
    fun `a database at the current version records Current on AppDb choice`() {
        plantCurrentDatabase()

        openGuarded()

        assertEquals(DatabaseChoice.Current(DB_NAME), AppDb.choice)
    }

    @Test
    fun `a database at the current version still opens the real file`() {
        plantCurrentDatabase()

        val appDb = openGuarded()

        assertNotNull(appDb.eventDao().getByIdNow(1, 100L))
    }

    @Test
    fun `a database at the current version creates no sidecar`() {
        plantCurrentDatabase()

        openGuarded()

        assertFalse(context.getDatabasePath(SIDECAR_NAME).exists())
    }

    @Test
    fun `a database at a lower version records Current on AppDb choice`() {
        plantV1Database()

        openGuarded()

        assertEquals(DatabaseChoice.Current(DB_NAME), AppDb.choice)
    }

    @Test
    fun `a database at a lower version still migrates, and its row survives`() {
        plantV1Database()

        val appDb = openGuarded()

        // if the guard had parked it, or the destructive fallback had fired, the row is not here
        assertNotNull(appDb.eventDao().getByIdNow(1, 100L))
    }

    /**
     * Opens through the production path, and makes Room open the file now rather than on a query.
     * [from] is the context `open` is handed; Room is always given its application context.
     */
    private fun openGuarded(from: Context = context): AppDb =
        AppDb.open(from, DB_NAME, APP_DB_VERSION).also {
            opened += it
            it.openHelper.writableDatabase
        }

    /**
     * A context whose own `getDatabasePath` points somewhere other than its application context's,
     * as a device-protected-storage context would. Room is handed the application context, so the
     * guard has to look in the directory Room will open from, not this one.
     */
    private fun contextWithAnotherDatabaseDirectory(): Context {
        val elsewhere = File(context.cacheDir, "another-database-directory")
        return object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File = File(elsewhere, name)
        }
    }

    /** The current schema with a row in two tables, `user_version` stamped higher than the build's. */
    private fun plantFutureDatabase() {
        createCurrentDatabase(context, DB_NAME)
        insertEvent(context, DB_NAME, profileId = 1, eventId = 100L)
        insertProfile(context, DB_NAME, profileId = 1)
        setUserVersion(context, DB_NAME, FUTURE_VERSION)
    }

    /**
     * [plantFutureDatabase], switched to write-ahead logging and closed cleanly: what a newer
     * build leaves when it exits normally. SQLite removes the log and the index on the last close,
     * so the `.db` is all there is on disk.
     */
    private fun plantCleanWalFutureDatabase() {
        plantFutureDatabase()
        withRawDatabase(context, DB_NAME) { raw ->
            raw.rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
        }
        // the setup holds: the file says write-ahead log, and nothing sits beside it
        assertEquals(2, context.getDatabasePath(DB_NAME).readBytes()[WAL_FORMAT_OFFSET].toInt())
        assertEquals(setOf(DB_NAME), familySnapshot().keys)
    }

    /**
     * A future database whose newer `user_version` is still in the write-ahead log, with that log
     * left on disk and no `-shm`: what a newer build leaves when it is killed mid-session. Built
     * by copying a live writer's files, since closing it cleanly would fold the log away.
     */
    private fun plantHotWalFutureDatabase() {
        createCurrentDatabase(context, SCRATCH_NAME)
        insertEvent(context, SCRATCH_NAME, profileId = 1, eventId = 100L)
        insertProfile(context, SCRATCH_NAME, profileId = 1)
        val scratch = context.getDatabasePath(SCRATCH_NAME)
        val target = context.getDatabasePath(DB_NAME)
        val writer = SQLiteDatabase.openDatabase(
            scratch.path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        )
        try {
            writer.rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
            writer.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
            writer.execSQL("PRAGMA user_version = $FUTURE_VERSION")
            scratch.copyTo(target, overwrite = true)
            File(scratch.path + "-wal").copyTo(File(target.path + "-wal"), overwrite = true)
        } finally {
            writer.close()
        }
        // the setup holds: the main file still says the build's own version, so the newer one is
        // only in a log that is really there, and there is no index beside it
        assertEquals(APP_DB_VERSION, ByteBuffer.wrap(target.readBytes()).getInt(USER_VERSION_OFFSET))
        assertTrue(File(target.path + "-wal").length() > 0)
        assertEquals(setOf(DB_NAME, WAL_NAME), familySnapshot().keys)
    }

    /** The SQLite header, then bytes that are not a database: the version cannot be read. */
    private fun plantUnreadableDatabase() {
        val file = context.getDatabasePath(DB_NAME).also { it.parentFile?.mkdirs() }
        file.writeBytes(
            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(48) { 'd'.code.toByte() }
        )
    }

    private fun plantCurrentDatabase() {
        createCurrentDatabase(context, DB_NAME)
        insertEvent(context, DB_NAME, profileId = 1, eventId = 100L)
    }

    private fun plantV1Database() {
        createDatabase(context, DB_NAME, version = 1)
        insertV1Event(context, DB_NAME, profileId = 1, eventId = 100L)
    }

    /** Every file of the real database's family (`.db`, `-wal`, `-shm`, ...) by name, with its bytes. */
    private fun familySnapshot(): Map<String, ByteArray> =
        context.getDatabasePath(DB_NAME).parentFile?.listFiles().orEmpty()
            .filter { it.name.startsWith(DB_NAME) }
            .associate { it.name to it.readBytes() }

    private fun addedFiles(before: Map<String, ByteArray>, after: Map<String, ByteArray>): Set<String> =
        after.keys - before.keys

    private fun assertSameBytes(
        name: String,
        before: Map<String, ByteArray>,
        after: Map<String, ByteArray>,
    ) {
        // there before, so the comparison cannot pass for a file that was never planted
        assertTrue("$name was not planted", name in before)
        assertArrayEquals(before[name], after[name])
    }

    /** Rebuilds the schema committed as `AppDb`'s current version, `user_version` set to match. */
    private fun createCurrentDatabase(ctx: Context, name: String) =
        createDatabase(ctx, name, APP_DB_VERSION)

    /**
     * Rebuilds the schema committed as `<version>.json`. `AppDbMigrationTest` says why an earlier
     * version's file is a frozen fixture to restore from git, and not something to regenerate.
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

    /** Inserts with the v1 column list only: there is no `eventDateUnknown` yet. */
    private fun insertV1Event(ctx: Context, name: String, profileId: Int, eventId: Long) =
        withRawDatabase(ctx, name) { raw ->
            raw.execSQL(
                """INSERT INTO events (profileId, eventId, eventDate, eventTime, eventTopic,
                    eventColor, eventType, teacherId, subjectId, teamId, addedDate,
                    eventAddedManually, eventBlacklisted, eventIsDone, eventIsDownloaded, keep)
                    VALUES ($profileId, $eventId, '2026-06-20', NULL, 'zadanie z polskiego', NULL,
                    ${Event.TYPE_HOMEWORK}, 0, 0, 0, 0, 0, 0, 0, 1, 0)"""
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
        const val WAL_NAME = "$DB_NAME-wal"
        val SHM_AND_WAL = setOf("$DB_NAME-shm", WAL_NAME)

        /** What the guard names the sidecar of [DB_NAME]: the base name, then `-v` and the version. */
        const val SIDECAR_NAME = "future-database-test-v$APP_DB_VERSION.db"

        /** Where the hot-log fixture is written before its files are copied to [DB_NAME]. */
        const val SCRATCH_NAME = "future-database-scratch.db"

        /** Not `APP_DB_VERSION + 1`, so a version the guard reports cannot be a computed one. */
        const val FUTURE_VERSION = 7

        /** Where the file header keeps `user_version`: 4 bytes, big-endian. */
        const val USER_VERSION_OFFSET = 60

        /** Where the file header keeps its format version: 1 for a rollback journal, 2 for a log. */
        const val WAL_FORMAT_OFFSET = 18
    }
}
