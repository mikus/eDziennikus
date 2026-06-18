/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.announcements

import eu.mikus.edziennik.data.db.full.AnnouncementFull

sealed interface AnnouncementsUiState {
    data object Loading : AnnouncementsUiState
    data object Empty : AnnouncementsUiState
    data class Content(val announcements: List<AnnouncementFull>) : AnnouncementsUiState
}
