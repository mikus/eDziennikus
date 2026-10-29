/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [chooseDatabase], the decision that is made about the database file before Room has seen it.
 *
 * One test per row of the decision table, plus the two ways the probe could hurt the file it is
 * inspecting: it must not delete it, and it must not create one that was never there.
 *
 * The file-survives tests carry the deletion hazard. A 64-byte junk file with no SQLite magic is
 * stopped by the header check and never reaches `SQLiteDatabase`, so it cannot tell the no-op
 * error handler from the default one. A file that has the magic and then junk does reach the open,
 * where "file is not a database" is a corruption exception and the default handler would delete
 * the file: that is the test that goes red if the handler is swapped.
 *
 * Fixtures are rebuilt from the committed `<version>.json`, as `AppDbMigrationTest` does. It needs
 * a real `Context` and a real SQLite file, hence Robolectric (JUnit 4, via Vintage). Unit tests
 * run with the working directory set to `app/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DatabaseChoiceTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `an absent file chooses the real database`() {
        val file = context.getDatabasePath(DB_NAME)
        assertFalse(file.exists())

        assertEquals(
            DatabaseChoice.Current(DB_NAME),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
    }

    @Test
    fun `a zero-length file chooses the real database`() {
        val file = writeFile(context, DB_NAME, ByteArray(0))

        assertEquals(
            DatabaseChoice.Current(DB_NAME),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
        // left exactly as it was: the probe has no business touching a file it does not recognise
        assertTrue(file.exists())
        assertEquals(0L, file.length())
    }

    @Test
    fun `a file that is not SQLite chooses the real database`() {
        // 'd' x 64: long enough to hold a header, and not one. Room's own corruption path deals
        // with it from here.
        val file = writeFile(context, DB_NAME, ByteArray(64) { 'd'.code.toByte() })

        assertEquals(
            DatabaseChoice.Current(DB_NAME),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
    }

    @Test
    fun `a file that is not SQLite still exists after the probe`() {
        val junk = ByteArray(64) { 'd'.code.toByte() }
        val file = writeFile(context, DB_NAME, junk)

        chooseDatabase(file, DB_NAME, CURRENT_VERSION)

        assertTrue(file.exists())
        assertArrayEquals(junk, file.readBytes())
    }

    /**
     * The deletion hazard proper. The magic header gets this file past the header check, so it is
     * opened; SQLite reports it as not a database, Android treats that as corruption, and the
     * default `DatabaseErrorHandler` answers corruption by deleting the file. With the no-op
     * handler the probe only reports `Unreadable`, and the bytes are still there.
     */
    @Test
    fun `a file with the SQLite magic header but junk after it still exists after the probe`() {
        val bytes = magicThenJunk()
        val file = writeFile(context, DB_NAME, bytes)

        chooseDatabase(file, DB_NAME, CURRENT_VERSION)

        assertTrue(file.exists())
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `a database at the current version chooses the real database`() {
        val file = createDatabase(context, DB_NAME, schemaVersion = 3)

        assertEquals(
            DatabaseChoice.Current(DB_NAME),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
    }

    @Test
    fun `a database at a lower version chooses the real database`() {
        // a v2 file is a user who has not yet been through MIGRATION_2_3: Room's to migrate
        val file = createDatabase(context, DB_NAME, schemaVersion = 2)

        assertEquals(
            DatabaseChoice.Current(DB_NAME),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
    }

    @Test
    fun `a database at a higher version chooses the sidecar and reports its version`() {
        // 7, not CURRENT_VERSION + 1, so the reported number cannot be a computed one
        val file = createDatabase(context, DB_NAME, schemaVersion = 3, userVersion = 7)

        assertEquals(
            DatabaseChoice.Newer(SIDECAR_NAME, storedVersion = 7),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
    }

    @Test
    fun `a file with the SQLite magic header but junk after it chooses the sidecar`() {
        // the header says SQLite and the version cannot be read: no readable version, no Room
        val file = writeFile(context, DB_NAME, magicThenJunk())

        assertEquals(
            DatabaseChoice.Unreadable(SIDECAR_NAME),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
    }

    /**
     * The one decision the table does not make: a file that is there but cannot be read. A
     * directory where the file should be is a fixture that needs no permissions: `exists()` is true
     * and opening it for reading throws. `Current` here would hand a database that momentarily
     * could not be read to Room, which drops it if it turns out to be from the future.
     */
    @Test
    fun `a file that exists but cannot be read chooses the sidecar`() {
        val file = context.getDatabasePath(DB_NAME).also { it.mkdirs() }
        assertTrue(file.exists())

        assertEquals(
            DatabaseChoice.Unreadable(SIDECAR_NAME),
            chooseDatabase(file, DB_NAME, CURRENT_VERSION),
        )
        // looked at, not touched
        assertTrue(file.isDirectory)
    }

    /**
     * Why the version is read through SQLite and not out of the file header. A newer build's write
     * of `user_version` can still be in the write-ahead log when it is downgraded over: the main
     * file then still says the old number, and Room, which reads through the log, sees the new
     * one. The probe must see what Room would, and must not write the file while looking.
     */
    @Test
    fun `a newer version still held in the write-ahead log chooses the sidecar`() {
        val file = createDatabase(context, DB_NAME, schemaVersion = 3)
        // the plain overload, on purpose: it takes the default error handler, which is fine for a
        // fixture this test just wrote and is exactly what the probe itself must not do
        val writer = SQLiteDatabase.openDatabase(
            file.path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        )
        try {
            writer.rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
            writer.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
            writer.execSQL("PRAGMA user_version = 7")
            // the setup holds: the main file still says 3, and only the log says 7
            assertEquals(3, ByteBuffer.wrap(file.readBytes()).getInt(USER_VERSION_OFFSET))
            assertTrue(File(file.path + "-wal").length() > 0)
            val before = file.readBytes()

            val choice = chooseDatabase(file, DB_NAME, CURRENT_VERSION)

            assertEquals(DatabaseChoice.Newer(SIDECAR_NAME, storedVersion = 7), choice)
            assertArrayEquals(before, file.readBytes())
        } finally {
            writer.close()
        }
    }

    @Test
    fun `the probe creates nothing when no database exists`() {
        val file = context.getDatabasePath(DB_NAME)
        val directory = file.parentFile
        // null when the directory is not there: it must then still not be there afterwards
        val before = directory?.list()?.toSet()
        assertFalse(file.exists())

        chooseDatabase(file, DB_NAME, CURRENT_VERSION)

        assertEquals(before, directory?.list()?.toSet())
        assertFalse(file.exists())
        assertFalse(context.getDatabasePath(SIDECAR_NAME).exists())
    }

    /** Writes [bytes] as the database file [name], creating its directory if need be. */
    private fun writeFile(ctx: Context, name: String, bytes: ByteArray): File =
        ctx.getDatabasePath(name).also {
            it.parentFile?.mkdirs()
            it.writeBytes(bytes)
        }

    /** The 16-byte SQLite header, then 48 bytes that are not a page size or anything else. */
    private fun magicThenJunk(): ByteArray =
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(48) { 'd'.code.toByte() }

    /**
     * Rebuilds the schema committed as `<schemaVersion>.json`, with `user_version` set to
     * [userVersion]. Room only ever rewrites the file of the version `AppDb` currently declares, so
     * every earlier one is a frozen fixture it will not regenerate: restore one from git rather
     * than letting a build recreate it.
     */
    private fun createDatabase(
        ctx: Context,
        name: String,
        schemaVersion: Int,
        userVersion: Int = schemaVersion,
    ): File {
        val root = JsonParser.parseString(
            File("schemas/eu.mikus.edziennik.data.db.AppDb/$schemaVersion.json").readText()
        ).asJsonObject.getAsJsonObject("database")
        val file = ctx.getDatabasePath(name).also {
            it.parentFile?.mkdirs()
            it.delete()
        }
        // the plain overload again, on purpose: see the write-ahead log test
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
        raw.execSQL("PRAGMA user_version = $userVersion")
        raw.close()
        return file
    }

    private companion object {
        const val DB_NAME = "database-choice-test.db"

        /** The sidecar for [DB_NAME] at [CURRENT_VERSION]. */
        const val SIDECAR_NAME = "database-choice-test-v3.db"

        /** `AppDb`'s `version` today. The probe takes it as a parameter; `AppDb` is not read. */
        const val CURRENT_VERSION = 3

        /** Where the file header keeps `user_version`: 4 bytes, big-endian. */
        const val USER_VERSION_OFFSET = 60
    }
}
