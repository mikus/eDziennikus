package eu.mikus.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.mikus.edziennik.data.db.entity.Event

class Migration85 : Migration(84, 85) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // loginStoreType = 5 (formerly LoginType.EDUDZIENNIK.id; enum entry
        // purged together with the graveyard cleanup in Migration101 — any
        // remaining EDUDZIENNIK profile rows are deleted by that migration
        // before they could be hydrated). Literal kept to preserve the
        // historical 84→85 step's behavior verbatim.
        database.execSQL("DELETE FROM events WHERE eventAddedManually = 0 AND eventType = ${Event.TYPE_HOMEWORK} AND profileId IN (SELECT profileId FROM (SELECT profileId FROM profiles WHERE loginStoreType = 5) x)")
    }
}
