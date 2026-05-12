/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-30.
 */

package eu.mikus.edziennik.ui.attendance.models

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.ui.grades.models.ExpandableItemModel
import eu.mikus.edziennik.utils.models.Date

data class AttendanceDayRange(
        var rangeStart: Date,
        var rangeEnd: Date?,
        override val items: MutableList<AttendanceFull> = mutableListOf()
) : ExpandableItemModel<AttendanceFull>(items) {
    override var level = 1

    var lastAddedDate = 0L

    var hasUnseen: Boolean = false
        get() = field || items.any { it.baseType != Attendance.TYPE_PRESENT && !it.seen }
}
