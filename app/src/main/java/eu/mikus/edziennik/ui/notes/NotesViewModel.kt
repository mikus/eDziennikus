/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.ui.search.SearchMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class NotesViewModel(
    source: () -> Flow<List<Note>>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<NotesUiState> =
        combine(source(), query) { notes, q -> classify(notes, q) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState.Loading)

    fun setQuery(q: String) {
        query.value = q
    }

    /**
     * Filter (by [q]) + rank + group into [NoteRow]s. Never mutates [Note] and never calls
     * `Note.compareTo`; the ordering is replicated with a local comparator (owner-type ordinal asc,
     * null leads; then search relevance asc; then addedDate desc). A blank query keeps every note
     * (all tie on relevance, so order falls through to addedDate desc within each group).
     */
    private fun classify(notes: List<Note>, q: String): NotesUiState {
        if (notes.isEmpty()) return NotesUiState.Empty

        // (note, relevance); a blank query keeps all with a tie relevance. No trim: rank/filter on
        // the SAME query string the Screen highlights with, so the two stay in lockstep (legacy
        // SearchFilter never trims either — a padded query is a literal needle for both).
        val ranked: List<Pair<Note, Int>> =
            if (q.isBlank()) {
                notes.map { it to SearchMatch.NO_MATCH }
            } else {
                notes.mapNotNull { note ->
                    val relevance = SearchMatch.relevance(note.searchKeywords, q)
                    if (relevance == SearchMatch.NO_MATCH) null else note to relevance
                }
            }

        val sorted = ranked.sortedWith(
            compareBy<Pair<Note, Int>> { it.first.ownerType?.ordinal ?: -1 }
                .thenBy { it.second }
                .thenByDescending { it.first.addedDate }
        )

        val rows = buildRows(sorted.map { it.first })
        return NotesUiState.Content(rows, q, resultCount = sorted.size)
    }

    /** Walk the already-sorted notes, emitting a [NoteRow.Header] at each owner-type boundary for
     *  supported types only (no header for the leading null-owner run, nor for unsupported types). */
    private fun buildRows(sortedNotes: List<Note>): List<NoteRow> {
        val rows = ArrayList<NoteRow>(sortedNotes.size + 8)
        // prevType starts null so the leading null-owner run never triggers a header (null == null),
        // and the first real owner type does (type != null) — no separate "started" flag needed.
        var prevType: Note.OwnerType? = null
        for (note in sortedNotes) {
            val type = note.ownerType
            if (type != prevType) {
                if (type != null && type in HEADER_TYPES) rows.add(NoteRow.Header(type))
                prevType = type
            }
            rows.add(NoteRow.Item(note))
        }
        return rows
    }

    companion object {
        /** The owner types that have a real header label + icon (the rest throw in NoteManager). */
        private val HEADER_TYPES = setOf(
            Note.OwnerType.EVENT, Note.OwnerType.DAY, Note.OwnerType.LESSON, Note.OwnerType.MESSAGE,
            Note.OwnerType.GRADE, Note.OwnerType.ATTENDANCE, Note.OwnerType.BEHAVIOR, Note.OwnerType.ANNOUNCEMENT,
        )
    }

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotesViewModel(
                source = { App.db.noteDao().getAll(App.profileId).asFlow() },
            ) as T
    }
}
