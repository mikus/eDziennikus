/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.list

import eu.mikus.edziennik.data.db.full.MessageFull

/** One mailbox tab: its message [type] and the filtered+ranked [items]. */
data class MessageTab(val type: Int, val items: List<MessageFull>)

sealed interface MessagesUiState {
    data object Loading : MessagesUiState
    data class Content(val query: String, val tabs: List<MessageTab>) : MessagesUiState
}
