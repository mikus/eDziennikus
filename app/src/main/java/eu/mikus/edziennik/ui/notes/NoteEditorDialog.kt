/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-24.
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.notes

import android.view.LayoutInflater
import android.widget.LinearLayout
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.databinding.NoteDialogHeaderBinding
import eu.mikus.edziennik.ext.isNotNullNorBlank
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.base.FormDropdown
import eu.mikus.edziennik.ui.dialogs.base.FormDropdownItem
import eu.mikus.edziennik.ui.dialogs.base.RichTextFieldBridge
import eu.mikus.edziennik.ui.dialogs.settings.CheckboxRow
import eu.mikus.edziennik.utils.html.BetterHtml
import eu.mikus.edziennik.utils.managers.TextStylingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NoteEditorDialog(
    activity: AppCompatActivity,
    private val owner: Noteable?,
    private val editingNote: Note?,
    private val profileId: Int =
        owner?.getNoteOwnerProfileId()
            ?: editingNote?.profileId
            ?: 0,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "NoteEditorDialog"

    override fun getTitleRes(): Int? = null
    override fun isCancelable() = false
    override fun getPositiveButtonText() = R.string.save
    override fun getNeutralButtonText() = if (editingNote != null) R.string.remove else null
    override fun getNegativeButtonText() = R.string.cancel

    // Rich-text bridge configs, stashed once from each RichTextFieldBridge's onConfigReady; the
    // save path converts each field's spans back to HTML via textStylingManager.getHtmlText(cfg),
    // identical to the legacy topicStylingConfig/bodyStylingConfig usage.
    private lateinit var topicCfg: TextStylingManager.StylingConfigBase
    private lateinit var bodyCfg: TextStylingManager.StylingConfigBase

    // Compose-observable UI state read by Content() and mutated by the (non-composable) save path.
    private val bodyError = mutableStateOf<String?>(null)
    private val replaceChecked = mutableStateOf(editingNote?.replacesOriginal ?: false)
    private val selectedColor = mutableStateOf(
        Note.Color.values().firstOrNull { it.value == editingNote?.color } ?: Note.Color.NONE,
    )

    override suspend fun onPositiveClick(): Boolean {
        val profile = withContext(Dispatchers.IO) {
            app.db.profileDao().getByIdNow(profileId)
        } ?: return NO_DISMISS

        val note = buildNote(profile) ?: return NO_DISMISS

        // Cross-user note sharing was removed when SzkolnyApi was dropped from the fork; the
        // share/unshare progress-dialog branches went with it and saveNote() is a pure local-DB op.
        return app.noteManager.saveNote(
            activity = activity,
            note = note,
            teamId = owner?.getNoteShareTeamId(),
        )
    }

    override suspend fun onNeutralClick(): Boolean {
        // editingNote cannot be null, as the button is visible only when it isn't.
        val confirmation = suspendCoroutine<Boolean> { cont ->
            var result = false
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.notes_editor_confirmation_text)
                .setPositiveButton(R.string.yes) { _, _ -> result = true }
                .setNegativeButton(R.string.no, null)
                .setOnDismissListener { cont.resume(result) }
                .show()
        }
        if (!confirmation)
            return NO_DISMISS

        return app.noteManager.deleteNote(activity, editingNote ?: return NO_DISMISS)
    }

    private fun buildNote(profile: Profile): Note? {
        val ownerType = owner?.getNoteType() ?: Note.OwnerType.NONE
        val topic = topicCfg.editText.text?.toString()
        val body = bodyCfg.editText.text?.toString()
        val replace = replaceChecked.value && ownerType.canReplace

        if (body.isNullOrBlank()) {
            bodyError.value = app.getString(R.string.notes_editor_body_error)
            bodyCfg.editText.requestFocus()
            return null
        }
        bodyError.value = null

        val topicHtml = if (topic.isNotNullNorBlank())
            app.textStylingManager.getHtmlText(topicCfg)
        else null
        val bodyHtml = app.textStylingManager.getHtmlText(bodyCfg)

        // Cross-user note sharing was removed when SzkolnyApi was dropped; notes are local-only.
        return Note(
            profileId = profile.id,
            id = editingNote?.id ?: System.currentTimeMillis(),
            ownerType = owner?.getNoteType(),
            ownerId = owner?.getNoteOwnerId(),
            replacesOriginal = replace,
            topic = topicHtml,
            body = bodyHtml,
            color = selectedColor.value.value,
        )
    }

    @Composable
    override fun Content() {
        val app = activity.applicationContext as App
        val canReplace = owner?.getNoteType()?.canReplace == true

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Owner header (all Noteable types + owner-tap → details) stays legacy via configureHeader.
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

            // Section subtitle (native replacement for the note_dialog_subtitle include).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_note),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.notes_editor_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            HorizontalDivider()

            RichTextFieldBridge(
                app = app,
                activity = activity,
                hint = stringResource(R.string.notes_editor_topic),
                initialHtml = editingNote?.topic?.let { BetterHtml.fromHtml(activity, it, nl2br = true) },
                htmlMode = TextStylingManager.HtmlMode.SIMPLE,
                onConfigReady = { topicCfg = it },
                modifier = Modifier.fillMaxWidth(),
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            )

            RichTextFieldBridge(
                app = app,
                activity = activity,
                hint = stringResource(R.string.notes_editor_body),
                initialHtml = editingNote?.body?.let { BetterHtml.fromHtml(activity, it, nl2br = true) },
                htmlMode = TextStylingManager.HtmlMode.SIMPLE,
                onConfigReady = { bodyCfg = it },
                modifier = Modifier.fillMaxWidth(),
                error = bodyError.value,
                minLines = 2,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            )

            if (canReplace) {
                CheckboxRow(
                    labelRes = R.string.notes_editor_replace_text,
                    checked = replaceChecked.value,
                    onCheckedChange = { replaceChecked.value = it },
                )
                Text(
                    stringResource(R.string.notes_editor_replace_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FormDropdown(
                hint = stringResource(R.string.notes_editor_color),
                items = Note.Color.values().map { color ->
                    FormDropdownItem(
                        id = color.value ?: 0L,
                        text = stringResource(color.stringRes),
                        leadingColorInt = color.value?.toInt(),
                        tag = color,
                    )
                },
                selectedId = selectedColor.value.value ?: 0L,
                onSelect = { item -> selectedColor.value = item.tag as? Note.Color ?: Note.Color.NONE },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
