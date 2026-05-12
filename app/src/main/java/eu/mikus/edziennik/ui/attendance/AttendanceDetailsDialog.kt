/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-9.
 */

package eu.mikus.edziennik.ui.attendance

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.databinding.AttendanceDetailsDialogBinding
import eu.mikus.edziennik.ext.setTintColor
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.ui.notes.setupNotesButton
import eu.mikus.edziennik.utils.BetterLink
import eu.mikus.edziennik.utils.managers.NoteManager

class AttendanceDetailsDialog(
    activity: AppCompatActivity,
    private val attendance: AttendanceFull,
    private val showNotes: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<AttendanceDetailsDialogBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "AttendanceDetailsDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        AttendanceDetailsDialogBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close

    override suspend fun onShow() {
        val manager = app.attendanceManager

        val attendanceColor = manager.getAttendanceColor(attendance)
        b.attendance = attendance
        b.devMode = App.devMode
        b.attendanceName.setTextColor(if (ColorUtils.calculateLuminance(attendanceColor) > 0.3) 0xaa000000.toInt() else 0xccffffff.toInt())
        b.attendanceName.background.setTintColor(attendanceColor)

        b.attendanceIsCounted.setText(if (attendance.isCounted) R.string.yes else R.string.no)

        attendance.teacherName?.let { name ->
            BetterLink.attach(
                b.teacherName,
                teachers = mapOf(attendance.teacherId to name),
                onActionSelected = dialog::dismiss
            )
        }

        b.notesButton.isVisible = showNotes
        b.notesButton.setupNotesButton(
            activity = activity,
            owner = attendance,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        )
        b.legend.isVisible = showNotes
        if (showNotes)
            NoteManager.setLegendText(attendance, b.legend)
    }
}
