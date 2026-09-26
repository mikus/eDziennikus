/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.config.configFlow
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Timetable state holder. Android-free (its Factory is the only App.* reader).
 *
 * - [dayFlow] is a per-date factory: lessons and [configFlow] are the reactive sources; events +
 *   attendance are snapshot-fetched per emission of either (AttendanceDao has no reactive query).
 *   Re-sync rewrites lessons -> re-fetch -> grid refresh; a config write re-derives with no new data.
 * - [requestedDate]/[currentDate] bridge the Fragment's FAB & bottom-sheet to the Compose pager.
 * - [markSeen] persists off-main, guarded + idempotent, only for non-normal unseen lessons.
 */
class TimetableViewModel(
    private val lessonsSource: (Date) -> Flow<List<LessonFull>>,
    private val eventsFetch: suspend (Date) -> List<EventFull>,
    private val attendanceFetch: suspend (Date) -> List<AttendanceFull>,
    private val configFlow: Flow<TimetableDayBuilder.Config>,
    initialDate: Date,
    private val onMarkSeen: (LessonFull) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val seenIds = mutableSetOf<Long>()

    private val _requestedDate = MutableStateFlow<Date?>(null)
    val requestedDate: StateFlow<Date?> = _requestedDate.asStateFlow()

    private val _currentDate = MutableStateFlow(initialDate)
    val currentDate: StateFlow<Date> = _currentDate.asStateFlow()

    fun dayFlow(date: Date): Flow<TimetableDayUiState> =
        combine(lessonsSource(date), configFlow) { lessons, cfg ->
            TimetableDayBuilder.build(
                date, lessons, eventsFetch(date), attendanceFetch(date), cfg,
                // Lessons this session marked keep their dot until the screen is left: marking
                // flips `seen`, the DAO re-emits, and without this every dot would disappear the
                // moment the user arrived on the day.
                stillShowUnseen = seenIds.toSet(),
            )
        }
            .flowOn(dispatcher)

    fun requestDate(date: Date) { _requestedDate.value = date }
    fun clearRequestedDate() { _requestedDate.value = null }
    fun onPageChanged(date: Date) { _currentDate.value = date }

    fun markSeen(lesson: LessonFull) {
        if (lesson.type <= eu.mikus.edziennik.data.db.entity.Lesson.TYPE_NORMAL) return
        if (lesson.seen || lesson.id in seenIds) return
        seenIds += lesson.id
        lesson.seen = true
        viewModelScope.launch(dispatcher) { onMarkSeen(lesson) }
    }

    /**
     * @param defaultStartHour/[defaultEndHour] the lesson-range seed computed off-main by the host
     *   (min/max of lessonRangeDao().getAllNow, falling back to the host's DEFAULT_* constants) —
     *   always passed explicitly, so the VM never imports the Fragment (dependency arrow stays host→VM).
     */
    class Factory(
        appContext: Context,
        private val initialDate: Date,
        private val defaultStartHour: Int,
        private val defaultEndHour: Int,
    ) : ViewModelProvider.Factory {
        private val app = appContext.applicationContext as App

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Capture once: the flow must observe the SAME ProfileConfig instance that readConfig
            // reads, or a profile switch leaves it listening to one profile and reading another.
            val profileConfig = app.profile.config
            fun readConfig() = profileConfig.ui.let {
                TimetableDayBuilder.Config(
                    trimHourRange = it.timetableTrimHourRange,
                    showEvents = it.timetableShowEvents,
                    showAttendance = it.timetableShowAttendance,
                    defaultStartHour = defaultStartHour,
                    defaultEndHour = defaultEndHour,
                )
            }
            return TimetableViewModel(
                lessonsSource = { date ->
                    app.db.timetableDao().getAllForDate(App.profileId, date).asFlow()
                        .map { lessons -> lessons.onEach { it.filterNotes() } }
                },
                eventsFetch = { date -> app.db.eventDao().getAllByDateNow(App.profileId, date) },
                attendanceFetch = { date -> app.db.attendanceDao().getAllByDateNow(App.profileId, date) },
                configFlow = configFlow(app.config, profileConfig) { readConfig() },
                initialDate = initialDate,
                onMarkSeen = { App.db.metadataDao().setSeen(App.profileId, it, true) },
            ) as T
        }
    }
}
