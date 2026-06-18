/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.full.AnnouncementFull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AnnouncementsViewModel(
    source: (Int) -> Flow<List<AnnouncementFull>>,
    profileId: Int,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val uiState: StateFlow<AnnouncementsUiState> =
        source(profileId)
            .map { classify(applyNoteFilter(it)) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnnouncementsUiState.Loading)

    /** Side-effecting step (named to reflect it): filterNotes() mutates each caller-owned
     *  AnnouncementFull in place — the single mutation site — then the same list is returned. */
    private fun applyNoteFilter(list: List<AnnouncementFull>): List<AnnouncementFull> {
        list.forEach { it.filterNotes() }
        return list
    }

    /** Pure classification. */
    private fun classify(list: List<AnnouncementFull>): AnnouncementsUiState =
        if (list.isEmpty()) AnnouncementsUiState.Empty else AnnouncementsUiState.Content(list)

    /**
     * Production factory: reads the App globals here and ONLY here. asFlow() (LiveData -> Flow)
     * lives in this lambda, keeping the ViewModel itself free of Android/LiveData so it stays
     * pure-Jupiter testable.
     */
    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AnnouncementsViewModel(
                source = { pid -> App.db.announcementDao().getAll(pid).asFlow() },
                profileId = App.profileId,
            ) as T
    }
}
