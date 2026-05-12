/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-23.
 */

package eu.mikus.edziennik.ui.notes

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.databinding.NoteListDialogBinding
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.utils.SimpleDividerItemDecoration

class NoteListDialog(
    activity: AppCompatActivity,
    private val owner: Noteable,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<NoteListDialogBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "NoteListDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        NoteListDialogBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun getNeutralButtonText() = R.string.add

    private val manager
        get() = app.noteManager

    private lateinit var adapter: NoteListAdapter

    override suspend fun onNeutralClick(): Boolean {
        NoteEditorDialog(
            activity = activity,
            owner = owner,
            editingNote = null,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        ).show()
        return NO_DISMISS
    }

    override suspend fun onShow() {
        manager.configureHeader(activity, owner, b.header)

        adapter = NoteListAdapter(
            activity = activity,
            onNoteClick = {
                NoteDetailsDialog(
                    activity = activity,
                    owner = owner,
                    note = it,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener,
                ).show()
            },
            onNoteEditClick = {
                NoteEditorDialog(
                    activity = activity,
                    owner = owner,
                    editingNote = it,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener,
                ).show()
            },
        )

        app.db.noteDao().getAllFor(
            profileId = owner.getNoteOwnerProfileId(),
            ownerType = owner.getNoteType(),
            ownerId = owner.getNoteOwnerId()
        ).observe(activity) { notes ->

            // show/hide relevant views
            b.noteListLayout.isVisible = notes.isNotEmpty()
            b.noData.isVisible = notes.isEmpty()
            if (notes.isEmpty()) {
                return@observe
            }

            // apply the new note list
            adapter.setAllItems(notes)

            // configure the adapter & recycler view
            if (b.noteList.adapter == null) {
                b.noteList.adapter = adapter
                b.noteList.apply {
                    //setHasFixedSize(true)
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(context)
                    addItemDecoration(SimpleDividerItemDecoration(context))
                }
            } else {
                adapter.notifyDataSetChanged()
            }
        }
    }
}
