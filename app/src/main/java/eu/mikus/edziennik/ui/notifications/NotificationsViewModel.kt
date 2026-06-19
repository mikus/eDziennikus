/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Notification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NotificationsViewModel(
    source: () -> Flow<List<Notification>>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> =
        source()
            .map { classify(it) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState.Loading)

    private fun classify(list: List<Notification>): NotificationsUiState =
        if (list.isEmpty()) NotificationsUiState.Empty else NotificationsUiState.Content(list)

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotificationsViewModel(
                source = { App.db.notificationDao().getAll().asFlow() },
            ) as T
    }
}
