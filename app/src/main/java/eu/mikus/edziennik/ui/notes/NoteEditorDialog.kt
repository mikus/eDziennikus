/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-24.
 */

package eu.mikus.edziennik.ui.notes

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.databinding.NoteEditorDialogBinding
import eu.mikus.edziennik.ext.isNotNullNorBlank
import eu.mikus.edziennik.ext.resolveString
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.utils.TextInputDropDown
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase
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
) : BindingDialog<NoteEditorDialogBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "NoteEditorDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        NoteEditorDialogBinding.inflate(layoutInflater)

    override fun isCancelable() = false
    override fun getPositiveButtonText() = R.string.save
    override fun getNeutralButtonText() = if (editingNote != null) R.string.remove else null
    override fun getNegativeButtonText() = R.string.cancel

    private lateinit var topicStylingConfig: StylingConfigBase
    private lateinit var bodyStylingConfig: StylingConfigBase
    private val manager
        get() = app.noteManager
    private val textStylingManager
        get() = app.textStylingManager

    private var progressDialog: AlertDialog? = null

    override suspend fun onPositiveClick(): Boolean {
        val profile = withContext(Dispatchers.IO) {
            app.db.profileDao().getByIdNow(profileId)
        } ?: return NO_DISMISS

        val note = buildNote(profile) ?: return NO_DISMISS

        // Cross-user note sharing was removed when SzkolnyApi was dropped
        // from the fork. The share/unshare progress dialog branches went
        // with it; saveNote() is now a pure local-DB operation.
        val success = manager.saveNote(
            activity = activity,
            note = note,
            teamId = owner?.getNoteShareTeamId(),
        )
        return success
    }

    override suspend fun onNeutralClick(): Boolean {
        // editingNote cannot be null, as the button is visible

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

        return manager.deleteNote(activity, editingNote ?: return NO_DISMISS)
    }

    override suspend fun onShow() {
        manager.configureHeader(activity, owner, b.header)

        topicStylingConfig = StylingConfigBase(editText = b.topic, htmlMode = HtmlMode.SIMPLE)
        bodyStylingConfig = StylingConfigBase(editText = b.body, htmlMode = HtmlMode.SIMPLE)

        b.ownerType = owner?.getNoteType() ?: Note.OwnerType.NONE
        b.editingNote = editingNote

        b.color.clear().append(Note.Color.values().map { color ->
            TextInputDropDown.Item(
                id = color.value ?: 0L,
                text = color.stringRes.resolveString(activity),
                tag = color,
                icon = if (color.value != null)
                    IconicsDrawable(activity).apply {
                        icon = CommunityMaterial.Icon.cmd_circle
                        sizeDp = 24
                        colorInt = color.value.toInt()
                    } else null,
            )
        })
        b.color.select(id = editingNote?.color ?: 0L)

        textStylingManager.attachToField(
            activity = activity,
            textLayout = b.topicLayout,
            textEdit = b.topic,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        )
        textStylingManager.attachToField(
            activity = activity,
            textLayout = b.bodyLayout,
            textEdit = b.body,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        )
    }

    private fun buildNote(profile: Profile): Note? {
        val ownerType = owner?.getNoteType() ?: Note.OwnerType.NONE
        val topic = b.topic.text?.toString()
        val body = b.body.text?.toString()
        val color = b.color.selected?.tag as? Note.Color

        // Cross-user note sharing was removed when SzkolnyApi was dropped
        // from the fork; new notes are always created local-only.
        val replace = b.replaceSwitch.isChecked && ownerType.canReplace

        if (body.isNullOrBlank()) {
            b.bodyLayout.error = app.getString(R.string.notes_editor_body_error)
            b.body.requestFocus()
            return null
        }

        val topicHtml = if (topic.isNotNullNorBlank())
            textStylingManager.getHtmlText(topicStylingConfig)
        else null
        val bodyHtml = textStylingManager.getHtmlText(bodyStylingConfig)

        return Note(
            profileId = profile.id,
            id = editingNote?.id ?: System.currentTimeMillis(),
            ownerType = owner?.getNoteType(),
            ownerId = owner?.getNoteOwnerId(),
            replacesOriginal = replace,
            topic = topicHtml,
            body = bodyHtml,
            color = color?.value,
        )
    }
}
