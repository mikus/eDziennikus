/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-8.
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration87 : Migration(86, 87) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE attendances ADD COLUMN attendanceIsCounted INTEGER NOT NULL DEFAULT 1")
    }
}
