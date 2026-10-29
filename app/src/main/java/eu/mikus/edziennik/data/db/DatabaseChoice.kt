/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.data.db

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.IOException

/**
 * Which database file to open, decided from the file itself before Room has seen it.
 *
 * `AppDb.builder` ends in `fallbackToDestructiveMigration()`, and Room 2.4.3 treats a stored
 * `user_version` higher than the build's as a reason to drop every table. A file written by a newer
 * build must therefore never be handed to Room under its real name; it is left alone and the app
 * opens a [name] beside it instead.
 */
sealed interface DatabaseChoice {
    /** The name to open. */
    val name: String

    /** Open the real database: nothing is there, or what is there is Room's to migrate or reject. */
    data class Current(override val name: String) : DatabaseChoice

    /** The file was written by a newer build, at [storedVersion]. [name] is the sidecar. */
    data class Newer(override val name: String, val storedVersion: Int) : DatabaseChoice

    /** The file says it is SQLite and its version could not be read. [name] is the sidecar. */
    data class Unreadable(override val name: String) : DatabaseChoice
}

/**
 * Decides which database to open for [file], whose name is [baseName] and whose build declares
 * [currentVersion].
 *
 * | file state                                    | result                       |
 * |-----------------------------------------------|------------------------------|
 * | absent, or zero-length                        | `Current(baseName)`          |
 * | no `SQLite format 3\0` magic in the first 16  | `Current(baseName)`          |
 * | magic, version readable, <= [currentVersion]  | `Current(baseName)`          |
 * | magic, version readable, > [currentVersion]   | `Newer(sidecar, version)`    |
 * | magic, version not readable                   | `Unreadable(sidecar)`        |
 *
 * The sidecar is `baseName` without its `.db`, then `-v<currentVersion>.db`. A file that exists
 * but cannot be read at all is also `Unreadable`: that is outside the table, and the side that
 * cannot destroy anything is the one to take.
 *
 * The header is checked by hand first so that a file that is not a database at all still reaches
 * Room as before, and Room's own corruption handling stays what deals with it.
 *
 * The version is read through SQLite and not out of bytes 60-63 of the file: a newer build's write
 * of `user_version` can still be in the write-ahead log, where the main file still holds the old
 * number and Room would see the new one.
 *
 * This only reads. It never creates, deletes, renames, moves or writes the database file, and it
 * does not log: `App.devMode` is assigned after the database is built, so `Utils.d` would be
 * silent here. SQLite itself may leave a `-shm` and an empty `-wal` beside a WAL-mode database it
 * has opened, even read-only; they are what Room's own open would create.
 */
internal fun chooseDatabase(file: File, baseName: String, currentVersion: Int): DatabaseChoice {
    val sidecar = baseName.removeSuffix(".db") + "-v" + currentVersion + ".db"
    if (!file.exists()) return DatabaseChoice.Current(baseName)
    val header = try {
        readHeader(file)
    } catch (e: IOException) {
        return DatabaseChoice.Unreadable(sidecar)
    }
    if (!header.contentEquals(SQLITE_MAGIC)) return DatabaseChoice.Current(baseName)
    val storedVersion = try {
        readUserVersion(file)
    } catch (e: SQLiteException) {
        return DatabaseChoice.Unreadable(sidecar)
    }
    return when {
        storedVersion == null -> DatabaseChoice.Unreadable(sidecar)
        storedVersion > currentVersion -> DatabaseChoice.Newer(sidecar, storedVersion)
        else -> DatabaseChoice.Current(baseName)
    }
}

/** `SQLite format 3` and a NUL: the first 16 bytes of every SQLite database file. */
private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/**
 * Handed to `openDatabase` in place of the default, and **empty on purpose**.
 *
 * `SQLiteDatabase` calls `onCorruption` when an open or a query hits a corruption exception, and
 * on Android "file is not a database" is one. `DefaultDatabaseErrorHandler.onCorruption` answers
 * by deleting the file, in both of its branches. Opening the probe's target with the default would
 * let the probe destroy exactly the data this file exists to protect, so this does nothing: not
 * log, not close, not delete. The exception still propagates and becomes `Unreadable`.
 *
 * Passing `null` to `openDatabase` selects the default, and is a defect.
 */
private val noOpErrorHandler = DatabaseErrorHandler { }

/** Up to the first 16 bytes of [file], fewer if it is shorter. */
private fun readHeader(file: File): ByteArray = file.inputStream().use { input ->
    val buffer = ByteArray(SQLITE_MAGIC.size)
    var read = 0
    while (read < buffer.size) {
        val count = input.read(buffer, read, buffer.size - read)
        if (count < 0) break
        read += count
    }
    buffer.copyOf(read)
}

/** `PRAGMA user_version` through a read-only connection, or null if the query returns no row. */
private fun readUserVersion(file: File): Int? {
    val db = SQLiteDatabase.openDatabase(
        file.path, null, SQLiteDatabase.OPEN_READONLY, noOpErrorHandler,
    )
    return try {
        db.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }
    } finally {
        db.close()
    }
}
