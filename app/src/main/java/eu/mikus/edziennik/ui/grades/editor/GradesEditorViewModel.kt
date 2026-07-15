/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.entity.Subject
import eu.mikus.edziennik.ui.grades.GradesEditorArgs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GradesEditorViewModel(
    private val loadSubject: suspend (Long) -> Subject?,
    private val loadGrades: suspend (Long) -> List<Grade>,
    private val args: GradesEditorArgs,
    private val dontCount: DontCountConfig,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<GradesEditorUiState>(GradesEditorUiState.Loading)
    val uiState: StateFlow<GradesEditorUiState> = _state.asStateFlow()

    private var subjectName: String = ""
    private var grades: List<EditorGrade> = emptyList()
    private var averageBefore: Float = 0f
    private var yearAverageBefore: Float = args.yearAverageBefore ?: 0f
    private val yearVisible: Boolean = args.averageMode != -1

    private val other = OtherSemester(
        sum = args.gradeSumOtherSemester ?: 0f,
        count = args.gradeCountOtherSemester ?: 0f,
        average = args.averageOtherSemester ?: 0f,
        final = args.finalOtherSemester ?: 0f,
    )

    init {
        viewModelScope.launch(dispatcher) { load(initial = true) }
    }

    private suspend fun load(initial: Boolean) {
        val subject = loadSubject(args.subjectId)
        if (subject == null || subject.id == -1L) {
            _state.value = GradesEditorUiState.SubjectMissing
            return
        }
        subjectName = subject.longName ?: ""
        grades = GradesEditorCalculator.toEditorGrades(loadGrades(args.subjectId), args.semester)
        if (initial) averageBefore = GradesEditorCalculator.semesterStats(grades, dontCount).average
        emit()
    }

    fun add(grade: EditorGrade) { grades = listOf(grade) + grades; emit() }

    fun remove(id: Long) { grades = grades.filterNot { it.id == id }; emit() }

    /** Change an existing simulated grade's name+value or weight. */
    fun edit(id: Long, name: String? = null, value: Float? = null, weight: Float? = null) {
        grades = grades.map {
            if (it.id != id) it
            else it.copy(name = name ?: it.name, value = value ?: it.value, weight = weight ?: it.weight)
        }
        emit()
    }

    fun restore() { viewModelScope.launch(dispatcher) { grades = GradesEditorCalculator.toEditorGrades(loadGrades(args.subjectId), args.semester); emit() } }

    private fun emit() {
        val sem = GradesEditorCalculator.semesterStats(grades, dontCount)
        _state.value = GradesEditorUiState.Content(
            subjectName = subjectName,
            semester = args.semester,
            averageBefore = averageBefore,
            averageAfter = sem.average,
            yearAverageVisible = yearVisible,
            yearAverageBefore = yearAverageBefore,
            yearAverageAfter = GradesEditorCalculator.yearAverage(args.averageMode, sem, other),
            grades = grades,
        )
    }

    class Factory(private val app: App, private val args: GradesEditorArgs) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cfg = app.profile.config.grades
            @Suppress("UNCHECKED_CAST")
            return GradesEditorViewModel(
                loadSubject = { withContext(Dispatchers.IO) { app.db.subjectDao().getByIdNow(App.profileId, it) } },
                loadGrades = { app.db.gradeDao().getAllBySubject(App.profileId, it).asFlow().first() },
                args = args,
                dontCount = DontCountConfig(
                    enabled = cfg.dontCountEnabled,
                    names = cfg.dontCountGrades.map { it.lowercase().trim() }.toSet(),
                ),
                dispatcher = Dispatchers.Main,
            ) as T
        }
    }
}
