/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-24.
 */
package eu.mikus.edziennik.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import eu.mikus.edziennik.utils.models.Date

@Entity(tableName = "luckyNumbers",
        primaryKeys = ["profileId", "luckyNumberDate"])
open class LuckyNumber(
        val profileId: Int,
        @ColumnInfo(name = "luckyNumberDate", typeAffinity = ColumnInfo.INTEGER)
        var date: Date,
        @ColumnInfo(name = "luckyNumber")
        var number: Int
) : Keepable() {
        @Ignore
        var showAsUnseen = false
}
