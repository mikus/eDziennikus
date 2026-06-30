/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.TeacherAbsenceFull
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgendaViewModel(
    eventsSource: () -> Flow<List<EventFull>>,
    lessonChangesSource: () -> Flow<List<LessonFull>>,
    teacherAbsenceSource: () -> Flow<List<TeacherAbsenceFull>>,
    private val config: AgendaBuilder.Config,
    selectedDateInitial: Date,
    private val onMarkAllSeen: () -> Unit,
    private val onMarkSeen: (EventFull) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(selectedDateInitial)
    val selectedDate: StateFlow<Date> = _selectedDate.asStateFlow()
    private val seenIds = mutableSetOf<Long>()   // single-thread confined (markSeen runs on main)

    val uiState: StateFlow<AgendaUiState> =
        combine(eventsSource(), lessonChangesSource(), teacherAbsenceSource(), _selectedDate) { e, lc, ta, sel ->
            AgendaBuilder.build(e, lc, ta, config, sel)
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState.Loading)

    fun setSelectedDate(date: Date) { _selectedDate.value = date }

    fun markSeen(event: EventFull) {
        if (event.seen || event.id in seenIds) return
        seenIds += event.id
        event.seen = true
        viewModelScope.launch(dispatcher) { onMarkSeen(event) }
    }

    fun markAllSeen() {
        viewModelScope.launch(dispatcher) { onMarkAllSeen() }
    }

    class Factory(appContext: Context) : ViewModelProvider.Factory {
        private val app = appContext.applicationContext as App

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val ui = app.profile.config.ui
            val cfg = AgendaBuilder.Config(
                agendaLessonChanges = ui.agendaLessonChanges,
                agendaTeacherAbsence = ui.agendaTeacherAbsence,
            )
            return AgendaViewModel(
                eventsSource = { app.db.eventDao().getAll(App.profileId).asFlow() },
                lessonChangesSource = {
                    if (cfg.agendaLessonChanges) app.db.timetableDao().getChanges(App.profileId).asFlow()
                    else flowOf(emptyList())
                },
                teacherAbsenceSource = {
                    if (cfg.agendaTeacherAbsence) app.db.teacherAbsenceDao().getAll(App.profileId).asFlow()
                    else flowOf(emptyList())
                },
                config = cfg,
                selectedDateInitial = Date.getToday(),
                onMarkAllSeen = { App.db.metadataDao().setAllSeen(App.profileId, MetadataType.EVENT, true) },
                onMarkSeen = { App.db.metadataDao().setSeen(App.profileId, it, true) },
            ) as T
        }
    }
}
