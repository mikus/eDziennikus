/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.behaviour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.data.db.full.NoticeFull
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

class BehaviourViewModel(
    source: () -> Flow<List<NoticeFull>>,
    private val onMarkSeen: (NoticeFull) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val filter = MutableStateFlow(SemesterFilter.YEAR)

    val uiState: StateFlow<BehaviourUiState> =
        combine(source(), filter) { notices, f -> classify(notices, f) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BehaviourUiState.Loading)

    fun setFilter(filter: SemesterFilter) {
        this.filter.value = filter
    }

    /**
     * Mark a notice seen via the injected write seam. Guarded + idempotent: an already-seen notice
     * is a no-op, so the render-time `LaunchedEffect` can fire freely without redundant DB writes.
     * Note: the write hits the `metadata` table, which `NoticeDao.getAll` does not observe, so the
     * unseen highlight only clears on the next visit (accepted legacy-equivalent semantics).
     */
    fun markSeen(notice: NoticeFull) {
        if (notice.seen) return
        notice.seen = true
        viewModelScope.launch(dispatcher) { onMarkSeen(notice) }
    }

    private fun classify(notices: List<NoticeFull>, filter: SemesterFilter): BehaviourUiState {
        val filtered = filter.semester?.let { sem -> notices.filter { it.semester == sem } } ?: notices
        val summary = BehaviourSummary(
            praises = filtered.count { it.type == Notice.TYPE_POSITIVE },
            warnings = filtered.count { it.type == Notice.TYPE_NEGATIVE },
            other = filtered.count { it.type == Notice.TYPE_NEUTRAL },
        )
        return BehaviourUiState.Content(filtered, summary, filter)
    }

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BehaviourViewModel(
                source = { App.db.noticeDao().getAll(App.profileId).asFlow() },
                onMarkSeen = { App.db.metadataDao().setSeen(App.profileId, it, true) },
            ) as T
    }
}
