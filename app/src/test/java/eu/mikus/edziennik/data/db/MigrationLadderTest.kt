/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.data.db

import androidx.room.RoomDatabase
import kotlin.test.fail
import org.junit.jupiter.api.Test

/**
 * The build-time gate for the forward direction of `fallbackToDestructiveMigration()`.
 *
 * [AppDb.builder] still ends in that call, and `RoomOpenHelper.onUpgrade` reaches it whenever
 * `findMigrationPath` returns null: Room does not fail on a missing path, it drops every table and
 * recreates them empty. Raising [APP_DB_VERSION] without registering the migration that leads to
 * it therefore compiles, passes every other test and ships, and the first update wipes every
 * user's local diary.
 *
 * Nothing in the platform stops that, because it is a developer's mistake and not something a
 * user does, so this test is what stops it. It asks the same question Room asks at upgrade time,
 * of the same [AppDb.MIGRATIONS] the builder registers, for every version a user could still be
 * on. It needs neither a database nor Android: `MigrationContainer` is plain Room API.
 */
class MigrationLadderTest {

    @Test
    fun `every version on the ladder can reach the current one`() {
        val container = RoomDatabase.MigrationContainer().apply { addMigrations(*AppDb.MIGRATIONS) }

        val stranded = (1 until APP_DB_VERSION).filter { from ->
            container.findMigrationPath(from, APP_DB_VERSION) == null
        }

        if (stranded.isEmpty()) return

        val registered = AppDb.MIGRATIONS
            .joinToString { "${it.startVersion} -> ${it.endVersion}" }
            .ifEmpty { "none" }
        // The highest stranded version is the first step the ladder is missing, wherever the gap
        // is: a missing tail (3 -> 4 after a bump to 4), a middle step, or a head (1 -> 2). It is
        // never the version below APP_DB_VERSION by default, which may already be registered.
        val missingFrom = stranded.max()
        fail(
            "APP_DB_VERSION is $APP_DB_VERSION, but no migration path reaches it from these " +
                "versions: ${stranded.joinToString()}. Registered in AppDb.MIGRATIONS: $registered. " +
                "AppDb.builder still ends in fallbackToDestructiveMigration(), so Room will not " +
                "fail on the gap: it drops and recreates every table, and every user silently " +
                "loses their local diary on update. Add the missing Migration to " +
                "AppDb.MIGRATIONS, normally Migration($missingFrom, ${missingFrom + 1}).",
        )
    }
}
