/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-25
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.mikus.edziennik.data.db.enums.MetadataType

class Migration68 : Migration(67, 68) {
    override fun migrate(database: SupportSQLiteDatabase) {
        /* Migration from crc16 to crc32 id */
        database.execSQL("DELETE FROM announcements")
        database.execSQL("DELETE FROM metadata WHERE thingType=${MetadataType.ANNOUNCEMENT.id}")
    }
}
