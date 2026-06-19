/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.teachers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Subject
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.isNotNullNorBlank
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TeachersViewModel(
    source: () -> Flow<List<Teacher>>,
    private val subjects: () -> List<Subject>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val uiState: StateFlow<TeachersUiState> =
        source()
            .map { classify(it, subjects()) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeachersUiState.Loading)

    /**
     * Pure mapping: sort (subjects-first, then real roles), then wrap each teacher with the
     * precomputed send-message flag. Subject/role display TEXT is intentionally NOT built here — it
     * is Context-bound (`Teacher.getTypeText`) and resolved at the composable edge. `subjects()` is
     * read on [dispatcher] (off the main thread) per emission — effectively a per-load snapshot.
     */
    private fun classify(teachers: List<Teacher>, subjects: List<Subject>): TeachersUiState {
        if (teachers.isEmpty()) return TeachersUiState.Empty
        val rows = teachers
            .sortedWith(compareBy({ it.subjects.isEmpty() }, { it.type == 0 }))
            .map { TeacherRow(it, it.loginId.isNotNullNorBlank()) }
        return TeachersUiState.Content(rows, subjects)
    }

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TeachersViewModel(
                source = { App.db.teacherDao().getAllTeachers(App.profileId).asFlow() },
                subjects = { App.db.subjectDao().getAllNow(App.profileId) },
            ) as T
    }
}
