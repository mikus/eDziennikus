/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-18.
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.event

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.widget.TextView
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.view.IconicsTextView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ext.isNotNullNorBlank
import eu.mikus.edziennik.ext.putExtras
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.base.SectionLabel
import eu.mikus.edziennik.ui.notes.setupNotesButton
import eu.mikus.edziennik.ui.timetable.TimetableFragment
import eu.mikus.edziennik.ui.views.AttachmentsView
import eu.mikus.edziennik.utils.BetterLink
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventDetailsDialog(
    activity: AppCompatActivity,
    private var event: EventFull,
    private val showNotes: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "EventDetailsDialog"
    override fun getTitleRes(): Int? = null
    override fun getPositiveButtonText() = R.string.close
    override fun getNeutralButtonText() = if (event.addedManually) R.string.remove else null

    override suspend fun onNeutralClick(): Boolean {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.are_you_sure)
            .setMessage(R.string.dialog_register_event_manual_remove_confirmation)
            .setPositiveButton(R.string.yes) { _, _ -> removeEvent() }
            .setNegativeButton(R.string.no, null)
            .show()
        return NO_DISMISS
    }

    private fun removeEvent() {
        launch {
            withContext(Dispatchers.Default) { app.db.eventDao().remove(event) }
            Toast.makeText(activity, R.string.removed, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun toggleDone(ev: EventFull) {
        if (!ev.isDone) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.event_mark_as_done_title)
                .setMessage(R.string.event_mark_as_done_text)
                .setPositiveButton(R.string.ok) { _, _ -> setDone(ev, true) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            setDone(ev, false)
        }
    }

    private fun setDone(ev: EventFull, done: Boolean) {
        ev.isDone = done
        launch(Dispatchers.Default) { app.db.eventDao().replace(ev) }
    }

    private fun editEvent(ev: EventFull) {
        EventManualDialog(
            activity,
            ev.profileId,
            editingEvent = ev,
            onSaveListener = { if (it == null) dialog.dismiss() },
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        ).show()
    }

    private fun goToTimetable(ev: EventFull) {
        dialog.dismiss()
        val dateStr = ev.date.stringY_m_d
        val intent = when {
            activity is MainActivity && activity.navTarget == NavTarget.TIMETABLE ->
                Intent(TimetableFragment.ACTION_SCROLL_TO_DATE)
            activity is MainActivity -> Intent("android.intent.action.MAIN")
            else -> Intent(activity, MainActivity::class.java)
        }
        intent.putExtras(
            "fragmentId" to NavTarget.TIMETABLE,
            "timetableDate" to dateStr,
        )
        if (activity is MainActivity) activity.sendBroadcast(intent) else activity.startActivity(intent)
    }

    private fun openInCalendar(ev: EventFull) {
        val title = (ev.typeName ?: "") +
            (if (ev.typeName.isNotNullNorBlank() && ev.subjectLongName.isNotNullNorBlank()) " - " else " ") +
            (ev.subjectLongName ?: "")
        val intent = Intent(Intent.ACTION_EDIT).apply {
            data = Events.CONTENT_URI
            putExtra(Events.TITLE, title)
            putExtra(Events.DESCRIPTION, ev.topicHtml.toString())
            if (ev.time == null) {
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.date.inMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ev.date.inMillis)
            } else {
                val startTime = ev.date.combineWith(ev.time)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTime + 45 * 60 * 1000)
            }
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.calendar_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    override fun Content() {
        val app = activity.applicationContext as App
        val observed by remember { app.db.eventDao().getById(event.profileId, event.id).asFlow() }
            .collectAsStateWithLifecycle(null)
        val ev = observed ?: event

        LaunchedEffect(observed) { observed?.let { event = it } }
        LaunchedEffect(ev.id) { if (!ev.seen) app.eventManager.markAsSeen(ev) }
        remember(ev) { ev.filterNotes() }

        val monthName = remember(ev) {
            runCatching { app.resources.getStringArray(R.array.months_day_of_array)[ev.date.month - 1] }.getOrNull()
        }
        val agendaSubjectImportant = ev.subjectLongName != null &&
            App.config[ev.profileId].ui.agendaSubjectImportant
        val name = if (agendaSubjectImportant) ev.subjectLongName else ev.typeName
        val details = listOfNotNull(
            if (agendaSubjectImportant) ev.typeName else ev.subjectLongName,
            ev.teamName,
        ).joinToString(" • ")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color(ev.eventColor)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    if (details.isNotBlank()) {
                        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ev.time?.stringHM ?: stringResource(R.string.event_all_day), style = MaterialTheme.typography.labelMedium)
                    Text(ev.date.day.toString(), fontSize = 36.sp, fontWeight = FontWeight.Light)
                    monthName?.let { Text(it) }
                }
            }

            // Legend
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx -> IconicsTextView(ctx) },
                update = { tv -> app.eventManager.setLegendText(tv, ev, showNotes) },
            )

            HorizontalDivider()

            // Teacher
            ev.teacherName?.let { teacher ->
                SectionLabel(stringResource(R.string.dialog_event_details_teacher))
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        TextView(ctx).apply {
                            text = teacher
                            BetterLink.attach(this, teachers = mapOf(ev.teacherId to teacher), onActionSelected = dialog::dismiss)
                        }
                    },
                )
            }

            // Added by
            SectionLabel(stringResource(R.string.dialog_event_details_added_by))
            Text(
                stringResource(
                    when {
                        ev.addedManually -> R.string.event_details_added_by_self_format
                        ev.teacherName == null -> R.string.event_details_added_by_unknown_format
                        else -> R.string.event_details_added_by_format
                    },
                    Date.fromMillis(ev.addedDate).formattedString,
                    ev.teacherName ?: "",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Topic (HTML + BetterLink)
            SectionLabel(stringResource(R.string.dialog_event_details_topic))
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx -> TextView(ctx) },
                update = { tv ->
                    tv.text = ev.topicHtml
                    BetterLink.attach(tv, onActionSelected = dialog::dismiss)
                },
            )

            // Homework body: lazily downloaded on open; rely on DB observe to re-render (no EventBus).
            val needsDownload = !ev.addedManually && (!ev.isDownloaded || (ev.isHomework && ev.homeworkBody == null))
            LaunchedEffect(needsDownload) {
                if (needsDownload) EdziennikTask.eventGet(ev.profileId, ev).enqueue(activity)
            }
            when {
                needsDownload -> {
                    SectionLabel(stringResource(R.string.dialog_event_details_body))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ev.homeworkBody.isNullOrBlank() -> Unit
                else -> {
                    SectionLabel(stringResource(R.string.dialog_event_details_body))
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx -> TextView(ctx) },
                        update = { tv ->
                            tv.text = ev.bodyHtml
                            BetterLink.attach(tv, onActionSelected = dialog::dismiss)
                        },
                    )
                }
            }

            // Attachments (self-contained AndroidView; keeps its own EventBus)
            if (!ev.attachmentIds.isNullOrEmpty() && !ev.attachmentNames.isNullOrEmpty()) {
                SectionLabel(stringResource(R.string.dialog_event_details_attachments))
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        AttachmentsView(ctx).apply {
                            init(
                                Bundle().also {
                                    it.putInt("profileId", ev.profileId)
                                    it.putLongArray("attachmentIds", ev.attachmentIds!!.toLongArray())
                                    it.putStringArray("attachmentNames", ev.attachmentNames!!.toTypedArray())
                                },
                                owner = ev,
                            )
                        }
                    },
                )
            }

            HorizontalDivider()

            // Action buttons
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { toggleDone(ev) }) {
                    IconicsIcon(
                        CommunityMaterial.Icon.cmd_eye_check_outline,
                        contentDescription = stringResource(R.string.hint_mark_as_done),
                        tint = if (ev.isDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (ev.addedManually) {
                    IconButton(onClick = { editEvent(ev) }) {
                        IconicsIcon(CommunityMaterial.Icon3.cmd_pencil_outline, contentDescription = stringResource(R.string.hint_edit_event))
                    }
                }
                IconButton(onClick = { openInCalendar(ev) }) {
                    IconicsIcon(CommunityMaterial.Icon.cmd_calendar_export, contentDescription = stringResource(R.string.hint_save_in_calendar))
                }
                IconButton(onClick = { goToTimetable(ev) }) {
                    IconicsIcon(CommunityMaterial.Icon.cmd_cursor_default_click_outline, contentDescription = stringResource(R.string.hint_go_to_timetable))
                }
                if (App.devMode) {
                    IconButton(onClick = { EdziennikTask.eventGet(ev.profileId, ev).enqueue(activity) }) {
                        IconicsIcon(CommunityMaterial.Icon.cmd_download_outline, contentDescription = stringResource(R.string.hint_download_again))
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
                                    owner = ev,
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
