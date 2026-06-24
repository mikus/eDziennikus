/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.homework

import eu.mikus.edziennik.data.db.full.EventFull

/** One row: the event + the unread flag captured at classify time (decoupled from later seen-mutation). */
data class HomeworkItem(val event: EventFull, val unseen: Boolean)

sealed interface HomeworkUiState {
    data object Loading : HomeworkUiState
    data class Content(
        val query: String,
        val current: List<HomeworkItem>,
        val past: List<HomeworkItem>,
    ) : HomeworkUiState
}
