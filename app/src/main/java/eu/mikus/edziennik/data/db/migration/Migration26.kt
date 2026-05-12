/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-25
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration26 : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE grades ADD gradePointGrade INTEGER NOT NULL DEFAULT 0;")
    }
}
