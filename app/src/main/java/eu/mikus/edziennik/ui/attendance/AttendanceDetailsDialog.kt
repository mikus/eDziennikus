/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-9.
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.attendance

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.button.MaterialButton
import com.mikepenz.iconics.view.IconicsTextView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.base.LabeledRow
import eu.mikus.edziennik.ui.dialogs.base.SectionLabel
import eu.mikus.edziennik.ui.notes.setupNotesButton
import eu.mikus.edziennik.utils.BetterLink
import eu.mikus.edziennik.utils.managers.NoteManager

class AttendanceDetailsDialog(
    activity: AppCompatActivity,
    private val attendance: AttendanceFull,
    private val showNotes: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "AttendanceDetailsDialog"
    override fun getTitleRes(): Int? = null
    override fun getPositiveButtonText() = R.string.close

    @Composable
    override fun Content() {
        val app = activity.applicationContext as App
        val color = Color(app.attendanceManager.getAttendanceColor(attendance))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: type pill + semester / subject
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(color, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = attendance.typeShort,
                        color = legibleTextColor(color),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.dialog_grade_details_semester_format, attendance.semester),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    attendance.subjectLongName?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Notes legend — setLegendText hides the view when there are no notes.
            if (showNotes) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx -> IconicsTextView(ctx) },
                    update = { tv -> NoteManager.setLegendText(attendance, tv) },
                )
            }

            // Teacher (BetterLink linkified, dismisses on action)
            attendance.teacherName?.let { name ->
                SectionLabel(stringResource(R.string.attendance_details_teacher))
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        TextView(ctx).apply {
                            text = name
                            BetterLink.attach(
                                this,
                                teachers = mapOf(attendance.teacherId to name),
                                onActionSelected = dialog::dismiss,
                            )
                        }
                    },
                )
            }

            LabeledRow(stringResource(R.string.attendance_details_type), attendance.typeName)
            LabeledRow(stringResource(R.string.attendance_details_date), attendance.date.formattedString)
            attendance.startTime?.let {
                LabeledRow(stringResource(R.string.attendance_details_time), it.stringHM)
            }
            attendance.lessonTopic?.let {
                LabeledRow(stringResource(R.string.attendance_details_lesson_topic), it)
            }
            LabeledRow(
                stringResource(R.string.attendance_details_is_counted),
                stringResource(if (attendance.isCounted) R.string.yes else R.string.no),
            )
            if (App.devMode) {
                LabeledRow(stringResource(R.string.attendance_details_id), attendance.id.toString())
                LabeledRow(stringResource(R.string.attendance_details_type_id), attendance.baseType.toString())
            }

            // Notes button — wrapped in a LinearLayout because setupNotesButton() casts its
            // layoutParams to LinearLayout.LayoutParams (verbatim from GradeDetailsDialog).
            if (showNotes) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            val button = MaterialButton(ctx)
                            addView(
                                button,
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ),
                            )
                            button.setupNotesButton(
                                activity = activity,
                                owner = attendance,
                                onShowListener = onShowListener,
                                onDismissListener = onDismissListener,
                            )
                        }
                    },
                )
            }
        }
    }
}
