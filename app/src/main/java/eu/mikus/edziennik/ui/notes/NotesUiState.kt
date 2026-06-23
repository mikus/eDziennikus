/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.notes

import eu.mikus.edziennik.data.db.entity.Note

sealed interface NotesUiState {
    data object Loading : NotesUiState
    data object Empty : NotesUiState

    /**
     * The grouped, filtered, ranked rows; the active [query]; and [resultCount] = the number of
     * [NoteRow.Item] rows (matching notes, excluding headers). A non-blank query matching nothing
     * yields empty [rows] with `resultCount == 0` (the Screen shows a "no results" message —
     * distinct from [Empty], which means the profile has no notes at all).
     */
    data class Content(
        val rows: List<NoteRow>,
        val query: String,
        val resultCount: Int,
    ) : NotesUiState
}

/** A two-type list model replacing the legacy synthetic `Note(isCategoryItem=true)` separators. */
sealed interface NoteRow {
    data class Header(val ownerType: Note.OwnerType) : NoteRow
    data class Item(val note: Note) : NoteRow
}
