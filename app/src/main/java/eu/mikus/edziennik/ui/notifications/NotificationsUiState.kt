/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.notifications

import eu.mikus.edziennik.data.db.entity.Notification

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState
    data object Empty : NotificationsUiState
    data class Content(val notifications: List<Notification>) : NotificationsUiState
}
