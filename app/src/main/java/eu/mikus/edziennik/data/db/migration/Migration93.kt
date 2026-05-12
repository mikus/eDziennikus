/*
 * Copyright (c) Kuba Szczodrzyński 2021-5-26.
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration93 : Migration(92, 93) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // notifications - long text
        database.execSQL("ALTER TABLE notifications ADD COLUMN textLong TEXT DEFAULT NULL;")
    }
}
