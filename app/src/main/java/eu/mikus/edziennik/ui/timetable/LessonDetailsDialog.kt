/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-11.
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.timetable

import android.content.Intent
import android.graphics.Typeface
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.view.IconicsTextView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ui.attendance.AttendanceDetailsDialog
import eu.mikus.edziennik.ui.attendance.AttendancePill
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.base.SectionLabel
import eu.mikus.edziennik.ui.event.EventDetailsDialog
import eu.mikus.edziennik.ui.event.EventManualDialog
import eu.mikus.edziennik.ui.event.EventRow
import eu.mikus.edziennik.ui.notes.setupNotesButton
import eu.mikus.edziennik.utils.BetterLink
import eu.mikus.edziennik.utils.managers.NoteManager
import eu.mikus.edziennik.utils.models.Week

class LessonDetailsDialog(
    activity: AppCompatActivity,
    private val lesson: LessonFull,
    private val attendance: AttendanceFull? = null,
    private val showNotes: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "LessonDetailsDialog"
    override fun getTitleRes(): Int? = null
    override fun getPositiveButtonText() = R.string.close
    override fun getNeutralButtonText() = R.string.add

    override suspend fun onNeutralClick(): Boolean {
        EventManualDialog(
            activity,
            lesson.profileId,
            defaultLesson = lesson,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        ).show()
        return NO_DISMISS
    }

    private fun markSeen(event: EventFull) {
        if (event.seen) return
        // Delegate to the manager (in-memory seen flag + 500 ms debounce + correct event.profileId),
        // exactly as the legacy EventListAdapter/EventViewHolder path and Task 5's EventDetailsDialog do.
        // Do NOT hand-roll metadataDao().setSeen(App.profileId, …) — App.profileId is the active profile,
        // which differs from lesson.profileId on the non-active-profile widget path (LessonDialogActivity).
        app.eventManager.markAsSeen(event)
    }

    @Composable
    override fun Content() {
        val app = activity.applicationContext as App
        val vis = lessonDetailVisibility(lesson.type)

        val lessonDate = lesson.displayDate ?: return
        val lessonTime = lesson.displayStartTime ?: return

        val events by remember {
            app.db.eventDao().getAllByDateTime(lesson.profileId, lessonDate, lessonTime).asFlow()
        }.collectAsStateWithLifecycle(emptyList())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Annotation banner (getAnnotation mutates the TextView + returns whether to show it)
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    TextView(ctx).apply {
                        setBackgroundResource(R.drawable.timetable_lesson_annotation)
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 12f
                        setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC))
                        val p = (8 * resources.displayMetrics.density).toInt()
                        setPadding(p, 0, p, 0)
                    }
                },
                update = { tv -> tv.isVisible = app.timetableManager.getAnnotation(activity, lesson, tv) },
            )

            // Header: (old→current) subject + date | number + time range
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (vis.showOldFields && lesson.oldSubjectId != null && lesson.subjectId != lesson.oldSubjectId) {
                        lesson.oldSubjectName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                    }
                    if (vis.showCurrentFields && lesson.displaySubjectId != null) {
                        lesson.subjectName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    }
                    Text(
                        Week.getFullDayName(lessonDate.weekDay) + ", " + lessonDate.formattedString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    lesson.displayLessonNumber?.let { num ->
                        Text(
                            stringResource(R.string.dialog_lesson_details_number),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(num.toString(), fontSize = 36.sp, fontWeight = FontWeight.Light)
                    }
                    Text("${lesson.displayStartTime?.stringHM} - ${lesson.displayEndTime?.stringHM}")
                }
            }

            // Shifted banner + go-to button
            if (vis.showShifted) {
                val otherLessonDate = when (lesson.type) {
                    Lesson.TYPE_SHIFTED_SOURCE -> lesson.date
                    Lesson.TYPE_SHIFTED_TARGET -> lesson.oldDate
                    else -> null
                }
                val shiftedText = when (lesson.type) {
                    Lesson.TYPE_SHIFTED_SOURCE -> when {
                        lesson.date != lesson.oldDate -> stringResource(
                            R.string.timetable_lesson_shifted_other_day,
                            lesson.date?.stringY_m_d ?: "?", lesson.startTime?.stringHM ?: "?",
                        )
                        lesson.startTime != lesson.oldStartTime -> stringResource(
                            R.string.timetable_lesson_shifted_same_day, lesson.startTime?.stringHM ?: "?",
                        )
                        else -> stringResource(R.string.timetable_lesson_shifted)
                    }
                    Lesson.TYPE_SHIFTED_TARGET -> when {
                        lesson.date != lesson.oldDate -> stringResource(
                            R.string.timetable_lesson_shifted_from_other_day,
                            lesson.oldDate?.stringY_m_d ?: "?", lesson.oldStartTime?.stringHM ?: "?",
                        )
                        lesson.startTime != lesson.oldStartTime -> stringResource(
                            R.string.timetable_lesson_shifted_from_same_day, lesson.oldStartTime?.stringHM ?: "?",
                        )
                        else -> stringResource(R.string.timetable_lesson_shifted_from)
                    }
                    else -> ""
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(shiftedText, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = {
                        dialog.dismiss()
                        val dateStr = otherLessonDate?.stringY_m_d ?: return@OutlinedButton
                        activity.sendBroadcast(
                            Intent(TimetableFragment.ACTION_SCROLL_TO_DATE)
                                .putExtra("timetableDate", dateStr),
                        )
                    }) { Text(stringResource(R.string.dialog_lesson_go_to_button)) }
                }
            }

            // Legend
            if (showNotes) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx -> IconicsTextView(ctx) },
                    update = { tv -> NoteManager.setLegendText(lesson, tv) },
                )
            }

            // Teacher / team change rows
            Row(Modifier.fillMaxWidth()) {
                val showTeacher = lesson.teacherName != null || lesson.oldTeacherName != null
                if (showTeacher) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        SectionLabel(stringResource(R.string.dialog_lesson_details_teacher))
                        if (vis.showOldFields && lesson.oldTeacherId != null && lesson.teacherId != lesson.oldTeacherId) {
                            lesson.oldTeacherName?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }
                        if (vis.showCurrentFields && lesson.displayTeacherId != null) {
                            lesson.teacherName?.let { name ->
                                AndroidView(
                                    modifier = Modifier.fillMaxWidth(),
                                    factory = { ctx ->
                                        TextView(ctx).apply {
                                            text = name
                                            BetterLink.attach(
                                                this,
                                                teachers = mapOf(lesson.displayTeacherId!! to name),
                                                onActionSelected = dialog::dismiss,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                val showTeam = lesson.teamName != null || lesson.oldTeamName != null
                if (showTeam) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel(stringResource(R.string.dialog_lesson_details_team))
                        if (vis.showOldFields && lesson.oldTeamId != null && lesson.teamId != lesson.oldTeamId) {
                            lesson.oldTeamName?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }
                        if (vis.showCurrentFields && lesson.displayTeamId != null) {
                            lesson.teamName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            // Classroom (note: legacy has NO type guard on the old-classroom row) + devMode id
            Row(Modifier.fillMaxWidth()) {
                val showClassroom = lesson.classroom != null || lesson.oldClassroom != null
                if (showClassroom) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel(stringResource(R.string.dialog_lesson_details_classroom))
                        if (lesson.oldClassroom != null && lesson.classroom != lesson.oldClassroom) {
                            Text(
                                lesson.oldClassroom!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                        if (vis.showCurrentFields && lesson.displayClassroom != null) {
                            lesson.classroom?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                if (App.devMode) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel(stringResource(R.string.dialog_lesson_details_id))
                        Text(lesson.id.toString(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Attendance section
            attendance?.let { att ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val useSymbols = app.attendanceManager.useSymbols
                    val attColor = Color(app.attendanceManager.getAttendanceColor(att))
                    AttendancePill(
                        text = if (useSymbols) att.typeSymbol else att.typeShort,
                        color = attColor,
                        big = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(att.typeName, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    app.attendanceManager.getAttendanceIcon(att)?.let { icon ->
                        eu.mikus.edziennik.ui.compose.IconicsIcon(icon, contentDescription = null, tint = attColor)
                        Spacer(Modifier.width(8.dp))
                    }
                    OutlinedButton(onClick = {
                        AttendanceDetailsDialog(
                            activity = activity,
                            attendance = att,
                            onShowListener = onShowListener,
                            onDismissListener = onDismissListener,
                        ).show()
                    }) { Text(stringResource(R.string.dialog_lesson_attendance_details)) }
                }
            }

            HorizontalDivider()

            // Events (reuse EventRow; mark-seen on appear; empty state)
            if (events.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.dialog_lesson_no_events),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.dialog_no_events_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                events.forEach { it.filterNotes() }
                Column(Modifier.fillMaxWidth()) {
                    events.forEach { ev ->
                        EventRow(
                            event = ev,
                            unseen = !ev.seen,
                            showWeekDay = false,
                            showDate = false,
                            showType = true,
                            showTime = true,
                            showSubject = true,
                            onClick = {
                                EventDetailsDialog(activity, it, onShowListener = onShowListener, onDismissListener = onDismissListener).show()
                            },
                            onEditClick = {
                                EventManualDialog(activity, it.profileId, editingEvent = it, onShowListener = onShowListener, onDismissListener = onDismissListener).show()
                            },
                            onAppear = ::markSeen,
                        )
                    }
                }
            }

            // Notes button
            if (showNotes) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.LinearLayout(ctx).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                val button = com.google.android.material.button.MaterialButton(ctx)
                                addView(
                                    button,
                                    android.widget.LinearLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ),
                                )
                                button.setupNotesButton(
                                    activity = activity,
                                    owner = lesson,
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
}
