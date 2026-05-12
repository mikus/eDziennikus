/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-25
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration40 : Migration(39, 40) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE profiles ADD changedEndpoints TEXT DEFAULT NULL")
    }
}
