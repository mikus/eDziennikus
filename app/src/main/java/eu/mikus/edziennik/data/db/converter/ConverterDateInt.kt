/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-17.
 */
package eu.mikus.edziennik.data.db.converter

import androidx.room.TypeConverter
import eu.mikus.edziennik.utils.models.Date

class ConverterDateInt {
    @TypeConverter
    fun toDate(value: Int): Date? = if (value == 0) null else Date.fromValue(value)

    @TypeConverter
    fun toInt(date: Date?): Int = date?.value ?: 0
}
