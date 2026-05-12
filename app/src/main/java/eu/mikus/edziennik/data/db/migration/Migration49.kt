/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-25
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration49 : Migration(48, 49) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE grades ADD gradeParentId INTEGER NOT NULL DEFAULT -1")
    }
}
