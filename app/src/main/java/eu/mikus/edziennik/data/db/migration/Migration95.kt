/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-1.
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration95 : Migration(94, 95) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // timetable - is extra flag
        database.execSQL("ALTER TABLE timetable ADD COLUMN isExtra INT NOT NULL DEFAULT 0;")
    }
}
