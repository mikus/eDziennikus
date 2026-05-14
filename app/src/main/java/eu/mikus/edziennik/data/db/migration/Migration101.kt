/*
 * Copyright (c) mikus 2026-05-14.
 */

package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration101 : Migration(100, 101) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Purge legacy profiles tied to removed login types:
        //   id = 3 (formerly LoginType.IDZIENNIK)
        //   id = 5 (formerly LoginType.EDUDZIENNIK)
        //
        // Those providers were already inactive when this fork
        // started, but their enum entries lingered as graveyard
        // sentinels for DB compatibility. With the entries removed
        // in this commit, any persisted profile referencing them
        // would NPE on hydration (Profile.loginStoreType is a
        // non-null enum field, deserialised through ConverterEnums).
        //
        // The Room schema declares no ON DELETE CASCADE foreign
        // keys for profile-scoped data, so we delete each child
        // table explicitly. Strategy follows the Migration94
        // temp-table pattern: stage the profileIds to purge in a
        // scratch table, fan-out DELETEs against it, then drop.
        //
        // loginStores does not have a profileId column; it is
        // joined via profiles.loginStoreId. We collect those IDs
        // separately and delete the parent rows last.

        database.execSQL("CREATE TABLE _101_ids (id INTEGER NOT NULL);")
        database.execSQL("INSERT INTO _101_ids SELECT profileId FROM profiles WHERE loginStoreType IN (3, 5);")

        database.execSQL("CREATE TABLE _101_storeIds (id INTEGER NOT NULL);")
        database.execSQL("INSERT INTO _101_storeIds SELECT DISTINCT loginStoreId FROM profiles WHERE loginStoreType IN (3, 5);")

        // delete from every profile-scoped child table
        database.execSQL("DELETE FROM grades WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM teachers WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM teacherAbsence WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM teacherAbsenceTypes WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM subjects WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM notices WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM teams WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM attendances WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM events WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM eventTypes WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM luckyNumbers WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM announcements WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM gradeCategories WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM messages WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM messageRecipients WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM endpointTimers WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM lessonRanges WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM notifications WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM classrooms WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM noticeTypes WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM attendanceTypes WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM timetable WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM config WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM librusLessons WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM timetableManual WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM notes WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM metadata WHERE profileId IN (SELECT id FROM _101_ids);")

        // delete the parent rows
        database.execSQL("DELETE FROM profiles WHERE profileId IN (SELECT id FROM _101_ids);")
        database.execSQL("DELETE FROM loginStores WHERE loginStoreId IN (SELECT id FROM _101_storeIds);")

        database.execSQL("DROP TABLE _101_ids;")
        database.execSQL("DROP TABLE _101_storeIds;")
    }
}
