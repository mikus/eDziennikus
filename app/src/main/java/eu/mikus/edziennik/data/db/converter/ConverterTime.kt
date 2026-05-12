package eu.mikus.edziennik.data.db.converter

import androidx.room.TypeConverter
import eu.mikus.edziennik.utils.models.Time

class ConverterTime {
    @TypeConverter
    fun toTime(value: String?): Time? = when (value) {
        null -> null
        "null" -> null
        else -> Time.fromHms(value)
    }

    @TypeConverter
    fun toString(value: Time?): String? = value?.stringValue
}
