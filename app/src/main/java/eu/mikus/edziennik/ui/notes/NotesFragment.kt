/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.databinding.NotesFragmentBinding
import eu.mikus.edziennik.ui.base.ScreenFab
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesFragment : Fragment() {

    companion object {
        private const val TAG = "NotesFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: NotesFragmentBinding? = null
    private lateinit var viewModel: NotesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = NotesFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, NotesViewModel.Factory)[NotesViewModel::class.java]

        activity.setScreenFab(ScreenFab(R.string.notes_action_add, CommunityMaterial.Icon3.cmd_text_box_plus_outline) {
            onNoteAddClick()
        })
        activity.gainAttentionFAB()

        b.notesCompose.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            NotesScreen(
                state = state,
                onQueryChange = viewModel::setQuery,
                onNoteClick = ::onNoteClick,
                onNoteEditClick = ::onNoteEditClick,
            )
        }
    }

    private fun onNoteAddClick() {
        NoteEditorDialog(activity = activity, owner = null, editingNote = null, profileId = App.profileId).show()
    }

    // Controller: resolve the owner off the main thread, then show the legacy dialog (the one real
    // coordination responsibility — mirrors the legacy NotesFragment + the Announcements precedent).
    private fun onNoteClick(note: Note) {
        viewLifecycleOwner.lifecycleScope.launch {
            val owner = withContext(Dispatchers.IO) { app.noteManager.getOwner(note) } as? Noteable
            NoteDetailsDialog(activity = activity, owner = owner, note = note).show()
        }
    }

    private fun onNoteEditClick(note: Note) {
        viewLifecycleOwner.lifecycleScope.launch {
            val owner = withContext(Dispatchers.IO) { app.noteManager.getOwner(note) } as? Noteable
            NoteEditorDialog(activity = activity, owner = owner, editingNote = note, profileId = App.profileId).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
