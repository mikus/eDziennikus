/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-24.
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.notes

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.databinding.NoteDialogHeaderBinding
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.base.LabeledRow
import eu.mikus.edziennik.ui.dialogs.base.SectionLabel
import eu.mikus.edziennik.utils.models.Date

class NoteDetailsDialog(
    activity: AppCompatActivity,
    private val owner: Noteable?,
    private var note: Note,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "NoteDetailsDialog"
    override fun getTitleRes(): Int? = null
    override fun getPositiveButtonText() = R.string.close
    // All notes are locally-authored now (no cross-user sharing), so editing is always allowed.
    override fun getNeutralButtonText() = R.string.homework_edit

    override suspend fun onNeutralClick(): Boolean {
        NoteEditorDialog(
            activity = activity,
            owner = owner,
            editingNote = note,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        ).show()
        return NO_DISMISS
    }

    @Composable
    override fun Content() {
        val app = activity.applicationContext as App
        // Reactive: watch the note; a null emission means it was deleted → dismiss.
        val liveNote by remember { app.db.noteDao().get(note.profileId, note.id).asFlow() }
            .collectAsStateWithLifecycle(note)
        LaunchedEffect(liveNote) {
            val current = liveNote
            if (current == null) dialog.dismiss() else note = current
        }

        liveNote?.let { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Owner header (all 8 Noteable types + owner-tap → details) stays legacy via configureHeader.
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                        val hb = NoteDialogHeaderBinding.inflate(LayoutInflater.from(ctx), container, true)
                        app.noteManager.configureHeader(activity, owner, hb)
                        hb.ownerItemList.isNestedScrollingEnabled = false
                        container
                    },
                )

                // Section subtitle (native replacement for the note_dialog_subtitle include)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_note),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.notes_details_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                HorizontalDivider()

                LabeledRow(
                    stringResource(R.string.dialog_event_details_added_by),
                    stringResource(
                        R.string.notes_added_by_you_format,
                        Date.fromMillis(current.addedDate).formattedString,
                        "",
                    ),
                )

                if (App.devMode) {
                    LabeledRow(stringResource(R.string.notes_details_id), current.id.toString())
                    LabeledRow(stringResource(R.string.notes_details_owner_id), current.ownerId.toString())
                }

                current.topic?.let {
                    SectionLabel(stringResource(R.string.dialog_event_details_topic))
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx -> TextView(ctx) },
                        update = { tv -> tv.text = current.topicHtml },
                    )
                }

                SectionLabel(stringResource(R.string.dialog_event_details_body))
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx -> TextView(ctx) },
                    update = { tv -> tv.text = current.bodyHtml },
                )
            }
        }
    }
}
