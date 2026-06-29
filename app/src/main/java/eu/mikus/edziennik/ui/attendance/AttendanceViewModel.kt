/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AttendanceViewModel(
    source: () -> Flow<List<AttendanceFull>>,
    private val config: AttendanceTreeBuilder.Config,
    periodInitial: Period,
    private val onMarkAllSeen: () -> Unit,
    private val onMarkSeen: (AttendanceFull) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _period = MutableStateFlow(periodInitial)
    val period: StateFlow<Period> = _period.asStateFlow()
    private val expandedNodes = MutableStateFlow<Set<NodeKey>>(emptySet())
    // mutated only from markSeen, which the Screen calls on the main thread (LaunchedEffect) — single-thread confined
    private val seenIds = mutableSetOf<Long>()

    val uiState: StateFlow<AttendanceUiState> =
        combine(source(), _period, expandedNodes) { att, p, exp ->
            withExpanded(AttendanceTreeBuilder.build(att, config, p), exp)
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceUiState.Loading)

    fun setPeriod(p: Period) { _period.value = p }

    fun toggleNode(key: NodeKey) {
        val current = expandedNodes.value
        expandedNodes.value = if (key in current) current - key else current + key
    }

    /**
     * Guarded/idempotent: flips in memory (synchronously, so a rapid double-call is deduped) + dispatches
     * the pure write off the main thread. The DAO observes only the attendance table, so the dot clears on
     * the next visit / expand-triggered rebuild (the Phase-5 model).
     */
    fun markSeen(att: AttendanceFull) {
        if (att.seen || att.id in seenIds) return
        seenIds += att.id
        att.seen = true
        viewModelScope.launch(dispatcher) { onMarkSeen(att) }
    }

    fun markAllSeen() {
        viewModelScope.launch(dispatcher) { onMarkAllSeen() }
    }

    private fun withExpanded(state: AttendanceUiState, expanded: Set<NodeKey>): AttendanceUiState {
        if (state !is AttendanceUiState.Content) return state
        return AttendanceUiState.Content(
            state.tabs.map { tab ->
                when (tab) {
                    is AttendanceTab.SummaryTab ->
                        tab.copy(subjects = tab.subjects.map { it.copy(expanded = it.key in expanded) })
                    is AttendanceTab.DaysTab ->
                        tab.copy(dayRanges = tab.dayRanges.map { it.copy(expanded = it.key in expanded) })
                    is AttendanceTab.MonthsTab ->
                        tab.copy(months = tab.months.map { it.copy(expanded = it.key in expanded) })
                    is AttendanceTab.TypesTab ->
                        tab.copy(types = tab.types.map { it.copy(expanded = it.key in expanded) })
                    is AttendanceTab.ListTab -> tab
                }
            },
        )
    }

    /** Host-constructed; the only App.* reader for the Android-free units. */
    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = appContext.applicationContext as App
            val attendanceConfig = app.profile.config.attendance
            return AttendanceViewModel(
                source = { app.db.attendanceDao().getAll(App.profileId).asFlow() },
                config = AttendanceTreeBuilder.Config(
                    groupConsecutiveDays = attendanceConfig.groupConsecutiveDays,
                    showPresenceInMonth = attendanceConfig.showPresenceInMonth,
                    currentSemester = app.profile.currentSemester,
                ),
                periodInitial = Period.ALL,
                onMarkAllSeen = {
                    App.db.metadataDao().setAllSeen(App.profileId, MetadataType.ATTENDANCE, true)
                },
                onMarkSeen = { App.db.metadataDao().setSeen(App.profileId, it, true) },
            ) as T
        }
    }
}
