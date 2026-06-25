/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.single

import eu.mikus.edziennik.data.db.full.MessageFull

sealed interface MessageReadUiState {
    data object Loading : MessageReadUiState
    data object NotFound : MessageReadUiState
    data class Content(val message: MessageFull) : MessageReadUiState
}
