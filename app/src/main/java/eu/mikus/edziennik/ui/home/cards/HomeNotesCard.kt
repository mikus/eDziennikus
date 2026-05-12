/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-28.
 */

package eu.mikus.edziennik.ui.home.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.plusAssign
import androidx.core.view.setMargins
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.*
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.databinding.CardHomeNotesBinding
import eu.mikus.edziennik.ext.dp
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.home.HomeCard
import eu.mikus.edziennik.ui.home.HomeCardAdapter
import eu.mikus.edziennik.ui.home.HomeFragment
import eu.mikus.edziennik.ui.notes.NoteDetailsDialog
import eu.mikus.edziennik.ui.notes.NoteEditorDialog
import eu.mikus.edziennik.ui.notes.NoteListAdapter
import eu.mikus.edziennik.utils.SimpleDividerItemDecoration
import kotlin.coroutines.CoroutineContext

class HomeNotesCard(
    override val id: Int,
    val app: App,
    val activity: MainActivity,
    val fragment: HomeFragment,
    val profile: Profile,
) : HomeCard, CoroutineScope {
    companion object {
        private const val TAG = "HomeNotesCard"
    }

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val manager
        get() = app.noteManager

    private lateinit var adapter: NoteListAdapter

    private fun onNoteClick(note: Note) = launch {
        val owner = withContext(Dispatchers.IO) {
            manager.getOwner(note)
        } as? Noteable

        NoteDetailsDialog(
            activity = activity,
            owner = owner,
            note = note,
        ).show()
    }

    private fun onNoteAddClick(view: View?) {
        NoteEditorDialog(
            activity = activity,
            owner = null,
            editingNote = null,
            profileId = profile.id,
        ).show()
    }

    override fun bind(position: Int, holder: HomeCardAdapter.ViewHolder) { launch {
        holder.root.removeAllViews()
        val b = CardHomeNotesBinding.inflate(LayoutInflater.from(holder.root.context))
        b.root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(8.dp)
        }
        holder.root += b.root

        adapter = NoteListAdapter(
            activity = activity,
            onNoteClick = this@HomeNotesCard::onNoteClick,
            onNoteEditClick = null,
        )

        app.db.noteDao().getAllNoOwner(profileId = profile.id).observe(activity) { notes ->

            // show/hide relevant views
            b.list.isVisible = notes.isNotEmpty()
            b.noData.isVisible = notes.isEmpty()
            if (notes.isEmpty()) {
                return@observe
            }

            // apply the new note list
            adapter.setAllItems(notes.take(4))

            // configure the adapter & recycler view
            if (b.list.adapter == null) {
                b.list.adapter = adapter
                b.list.apply {
                    //setHasFixedSize(true)
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(context)
                    addItemDecoration(SimpleDividerItemDecoration(context))
                }
            } else {
                adapter.notifyDataSetChanged()
            }
        }

        b.addNote.onClick(this@HomeNotesCard::onNoteAddClick)

        holder.root.onClick {
            activity.navigate(navTarget = NavTarget.NOTES)
        }
    }}

    override fun unbind(position: Int, holder: HomeCardAdapter.ViewHolder) = Unit
}
