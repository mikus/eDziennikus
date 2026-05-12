/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-22.
 */

package eu.mikus.edziennik.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

@Entity(tableName = "timetableManual",
        indices = [
            Index(value = ["profileId", "date"]),
            Index(value = ["profileId", "weekDay"])
        ])
class TimetableManual(
        val profileId: Int,
        var type: Int,
        var repeatBy: Int,

        @PrimaryKey(autoGenerate = true)
        var id: Int = 0
) {
    companion object {
        const val TYPE_NORMAL = 0
        const val TYPE_CANCELLED = 1
        const val TYPE_CHANGE = 2
        const val TYPE_SHIFTED_SOURCE = 3
        const val TYPE_SHIFTED_TARGET = 4
        const val TYPE_REMOVED = 5
        const val TYPE_CLASSROOM = 6
        const val REPEAT_WEEKLY = 0
        const val REPEAT_ONCE = 1
        const val REPEAT_BY_SUBJECT = 2
    }

    // `date` for one time lesson
    @ColumnInfo(typeAffinity = ColumnInfo.INTEGER)
    var date: Date? = null
    // `weekDay` for repeating lesson (every week)
    var weekDay: Int? = null

    var lessonNumber: Int? = null
    var startTime: Time? = null
    var endTime: Time? = null

    var subjectId: Long? = null
    var teacherId: Long? = null
    var teamId: Long? = null
    var classroom: String? = null

    fun verifyParams(): Boolean {
        return when (repeatBy) {
            REPEAT_WEEKLY -> date == null && weekDay != null
            REPEAT_ONCE -> date != null && weekDay == null
            REPEAT_BY_SUBJECT -> date == null && weekDay == null && subjectId != null
            else -> false
        }
    }
}
