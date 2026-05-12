/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-8.
 */

package eu.mikus.edziennik.ui.attendance.models

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.entity.AttendanceType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.ui.grades.models.ExpandableItemModel

data class AttendanceTypeGroup(
        val type: AttendanceType,
        override val items: MutableList<AttendanceFull> = mutableListOf()
) : ExpandableItemModel<AttendanceFull>(items) {
    override var level = 1

    var lastAddedDate = 0L

    var hasUnseen: Boolean = false
        get() = field || items.any { it.baseType != Attendance.TYPE_PRESENT && !it.seen }

    var percentage: Float = 0f
    var semesterCount: Int = 0
}
