/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ui.search.SearchMatch
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeworkViewModel(
    source: () -> Flow<List<EventFull>>,
    private val today: () -> Date,
    private val onMarkSeen: (EventFull) -> Unit,   // PURE write seam; the VM owns the in-memory flip
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<HomeworkUiState> =
        combine(source(), query) { all, q -> classify(all, q) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeworkUiState.Loading)

    fun setQuery(q: String) {
        query.value = q
    }

    /**
     * Mark seen via the pure write seam; the VM owns the in-memory flip. Guarded + idempotent so the row's
     * onAppear LaunchedEffect can fire freely. Identical to BehaviourViewModel.markSeen; the metadata write
     * does not re-emit the list (RawQuery observes Event only), so the highlight clears on the next visit.
     */
    fun markSeen(event: EventFull) {
        if (event.seen) return
        event.seen = true
        viewModelScope.launch(dispatcher) { onMarkSeen(event) }
    }

    /**
     * Partition all homework into Current/Past (legacy predicates vs [today]), filter+rank+sort each by [q],
     * and capture the unread flag. Jupiter-pure (no realized HTML): the one intentional mutation is the
     * per-event [EventFull.filterNotes] call (an in-place filter of each event's `notes` list,
     * legacy-equivalent); `searchKeywords` is touched but its lazy `BetterHtml` stays unrealized off-device
     * (mocked in tests). Never mutates
     * `searchPriority`; never clones [EventFull] (the immutability is in the [HomeworkItem] wrapper).
     */
    private fun classify(all: List<EventFull>, q: String): HomeworkUiState {
        all.forEach { it.filterNotes() }
        val todayDate = today()
        val current = ArrayList<EventFull>(all.size)
        val past = ArrayList<EventFull>(all.size)
        for (event in all) {
            if (!event.isDone && event.date >= todayDate) current.add(event) else past.add(event)
        }
        return HomeworkUiState.Content(
            query = q,
            current = rank(current, q, reversed = false),
            past = rank(past, q, reversed = true),
        )
    }

    /** Filter (by [q]) + rank + sort one partition into [HomeworkItem]s. Blank [q] keeps all (tie relevance). */
    private fun rank(events: List<EventFull>, q: String, reversed: Boolean): List<HomeworkItem> {
        val ranked: List<Pair<EventFull, Int>> =
            if (q.isBlank()) {
                events.map { it to SearchMatch.NO_MATCH }
            } else {
                events.mapNotNull { event ->
                    val relevance = SearchMatch.relevance(event.searchKeywords, q)
                    if (relevance == SearchMatch.NO_MATCH) null else event to relevance
                }
            }
        // Relevance ascending (best first) in BOTH partitions — reproduces the legacy SearchFilter
        // priority-negation + SearchableAdapter.sortedDescending pipeline. Only date/time/addedDate flip
        // direction for Past; there is no whole-list reverse (that would invert relevance).
        val comparator = if (reversed) {
            compareBy<Pair<EventFull, Int>> { it.second }
                .thenByDescending { it.first.date }
                .thenByDescending { it.first.time?.value ?: 0 }
                .thenByDescending { it.first.addedDate }
        } else {
            compareBy<Pair<EventFull, Int>> { it.second }
                .thenBy { it.first.date }
                .thenBy { it.first.time?.value ?: 0 }
                .thenBy { it.first.addedDate }
        }
        return ranked.sortedWith(comparator).map { HomeworkItem(it.first, unseen = !it.first.seen) }
    }

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeworkViewModel(
                source = { App.db.eventDao().getAllByType(App.profileId, Event.TYPE_HOMEWORK).asFlow() },
                today = { Date.getToday() },
                onMarkSeen = { App.db.metadataDao().setSeen(App.profileId, it, true) },
            ) as T
    }
}
