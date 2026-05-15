/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-24.
 */

package eu.mikus.edziennik.ui.notes

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.databinding.NoteDetailsDialogBinding
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.utils.models.Date

class NoteDetailsDialog(
    activity: AppCompatActivity,
    private val owner: Noteable?,
    private var note: Note,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<NoteDetailsDialogBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "NoteDetailsDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        NoteDetailsDialogBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    // All notes are locally-authored now (no cross-user sharing), so editing
    // is always allowed.
    override fun getNeutralButtonText() = R.string.homework_edit

    private val manager
        get() = app.noteManager

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

    override suspend fun onShow() {
        manager.configureHeader(activity, owner, b.header)

        b.idsLayout.isVisible = App.devMode

        // watch the note for changes
        app.db.noteDao().get(note.profileId, note.id).observe(activity) {
            if (it == null) {
                dismiss()
                return@observe
            }
            note = it
            update()
        }
    }

    private fun update() {
        b.note = note

        if (note.color != null) {
            dialog.overlayBackgroundColor(note.color!!.toInt(), 0x50)
        } else {
            dialog.overlayBackgroundColor(0, 0)
        }

        b.addedBy.setText(
            R.string.notes_added_by_you_format,
            Date.fromMillis(note.addedDate).formattedString,
            "",
        )
    }
}
