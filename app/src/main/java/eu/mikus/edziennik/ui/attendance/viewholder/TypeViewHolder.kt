/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-8.
 */

package eu.mikus.edziennik.ui.attendance.viewholder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.databinding.AttendanceItemTypeBinding
import eu.mikus.edziennik.ext.concat
import eu.mikus.edziennik.ui.attendance.AttendanceAdapter
import eu.mikus.edziennik.ui.attendance.models.AttendanceTypeGroup
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder
import eu.mikus.edziennik.utils.models.Date

class TypeViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        val b: AttendanceItemTypeBinding = AttendanceItemTypeBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<AttendanceTypeGroup, AttendanceAdapter> {
    companion object {
        private const val TAG = "TypeViewHolder"
    }

    override fun onBind(activity: AppCompatActivity, app: App, item: AttendanceTypeGroup, position: Int, adapter: AttendanceAdapter) {
        val manager = app.attendanceManager

        val type = item.type
        b.title.text = type.typeName

        b.dropdownIcon.rotation = when (item.state) {
            AttendanceAdapter.STATE_CLOSED -> 0f
            else -> 180f
        }

        b.unread.isVisible = item.hasUnseen

        b.details.text = listOf(
                app.getString(R.string.attendance_percentage_format, item.percentage),
                app.getString(R.string.attendance_type_yearly_format, item.items.size),
                app.getString(R.string.attendance_type_semester_format, item.semesterCount)
        ).concat(" • ")

        b.type.setAttendance(Attendance(
                profileId = 0,
                id = 0,
                baseType = type.baseType,
                typeName = "",
                typeShort = type.typeShort,
                typeSymbol = type.typeSymbol,
                typeColor = type.typeColor,
                date = Date(0, 0, 0),
                startTime = null,
                semester = 0,
                teacherId = 0,
                subjectId = 0,
                addedDate = 0
        ), manager, bigView = false)
    }
}
