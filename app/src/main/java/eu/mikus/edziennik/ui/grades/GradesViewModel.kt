/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.config.ConfigGrades
import eu.mikus.edziennik.config.ProfileConfig
import eu.mikus.edziennik.config.configFlow
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.ui.grades.GradesTreeBuilder.Math
import eu.mikus.edziennik.ui.grades.GradesTreeBuilder.SemesterAvgInput
import eu.mikus.edziennik.ui.grades.models.GradesSemester
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the grades screen derives from config, in one value so `configFlow` can dedupe on it.
 *
 * [config] goes straight to the builder. [yearAverageMode] is read by `editorArgs` for the grades
 * editor's nav Bundle (GradesEditorViewModel.kt:88 computes `yearAverageAfter` from it). The other
 * five are **dedupe keys and nothing else**: they reach the tree through the [GradesTreeBuilder.Math]
 * lambdas, which delegate to `GradesManager` getters that re-read config at call time
 * (`GradesManager.kt:64-71`, `:113-127`, `:294`). Those live reads apply a new value for free — but
 * they produce no emission of their own, so without them here `distinctUntilChanged`
 * (`ConfigFlow.kt:27`) drops the write and the screen never re-derives.
 *
 * **Adding a key to GradesConfigDialog means adding it here.** `GradesInputsTest` asserts every field
 * by name, so deleting one is a compile failure rather than a silent regression.
 */
data class GradesInputs(
    val config: GradesTreeBuilder.Config,
    val plusValue: Float?,
    val minusValue: Float?,
    val averageWithoutWeight: Boolean,
    val yearAverageMode: Int,
    val dontCountEnabled: Boolean,
    val dontCountGrades: List<String>,
)

/**
 * Top-level on purpose: `Factory.create()` needs a real [App] and cannot be unit-tested, so a key
 * missing from a reader living inside it would stay green forever. Here `GradesInputsTest` drives the
 * real `configFlow` over the real reader.
 *
 * [globalGrades] and [profileConfig] are two different config instances — `orderBy` is the one GLOBAL
 * grades key (`ConfigGrades.kt:12`, stored as `gradesOrderBy`; read by `GradesManager.kt:59`), the
 * other eight are per-profile (`ProfileConfigGrades.kt:14-25`).
 */
internal fun readGradesInputs(
    globalGrades: ConfigGrades,
    profileConfig: ProfileConfig,
    isUniversity: Boolean,
    devMode: Boolean,
): GradesInputs {
    val g = profileConfig.grades
    return GradesInputs(
        config = GradesTreeBuilder.Config(
            isUniversity = isUniversity,
            hideNoGrade = g.hideNoGrade,
            hideSticksFromOldDevMode = g.hideSticksFromOld && devMode,
            hideImproved = g.hideImproved,
            orderBy = globalGrades.orderBy,
        ),
        plusValue = g.plusValue,
        minusValue = g.minusValue,
        averageWithoutWeight = g.averageWithoutWeight,
        yearAverageMode = g.yearAverageMode,
        dontCountEnabled = g.dontCountEnabled,
        dontCountGrades = g.dontCountGrades,
    )
}

class GradesViewModel(
    source: () -> Flow<List<GradeFull>>,
    math: Math,
    inputs: Flow<GradesInputs>,
    expandedSubjectInitial: Long,
    private val onMarkAllSeen: () -> Unit,
    private val onMarkSeen: (GradeFull) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val expandedSubjects = MutableStateFlow<Set<Long>>(emptySet())
    private val expandedSemesters = MutableStateFlow<Set<Pair<Long, Int>>>(emptySet())

    val uiState: StateFlow<GradesUiState> =
        combine(
            source().map { applyNoteFilter(it) }, expandedSubjects, expandedSemesters, inputs,
        ) { grades, subs, sems, i ->
            withExpanded(GradesTreeBuilder.build(grades, i.config, math), subs, sems)
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GradesUiState.Loading)

    /**
     * The newest flowed inputs, for the one reader that is NOT the tree: [editorArgs] puts
     * `yearAverageMode` into the GRADES_EDITOR nav Bundle, where GradesEditorViewModel.kt:88 computes
     * `yearAverageAfter` from it. `Eagerly`, not `WhileSubscribed`, because this flow has no collector
     * of its own — nothing would ever start it.
     */
    private val latestInputs: StateFlow<GradesInputs?> =
        inputs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Side-effecting step (named to reflect it, as in AnnouncementsViewModel): GradeFull's Room relation
     * joins on the row id alone, so a grade can arrive carrying notes owned by another profile or another
     * owner type; filterNotes() strips those in place. Sits on the source flow, not inside the combine, so
     * it runs once per DB emission rather than on every expand/collapse — and because it is the single
     * mutation site, everything downstream (the pure tree builder, the row's note glyph and substitute
     * text, and the GradeFull handed to GradeDetailsDialog) sees the same filtered list.
     */
    private fun applyNoteFilter(grades: List<GradeFull>): List<GradeFull> {
        grades.forEach { it.filterNotes() }
        return grades
    }

    init {
        // deep-link: open the target subject (subject + its first semester) once the tree is built,
        // via the SAME openSubject seam as a manual tap — then stop.
        if (expandedSubjectInitial != 0L) viewModelScope.launch(dispatcher) {
            uiState.first { it is GradesUiState.Content }
            openSubject(expandedSubjectInitial)
        }
    }

    /** The single subject-open seam (subject + its first non-empty semester); shared by deep-link + manual toggle. */
    private fun openSubject(subjectId: Long) {
        expandedSubjects.value = expandedSubjects.value + subjectId
        (uiState.value as? GradesUiState.Content)
            ?.subjects?.firstOrNull { it.subjectId == subjectId }
            ?.firstNonEmptySemesterNumber
            ?.let { expandedSemesters.value = expandedSemesters.value + (subjectId to it) }
    }

    private fun withExpanded(state: GradesUiState, subs: Set<Long>, sems: Set<Pair<Long, Int>>): GradesUiState {
        if (state !is GradesUiState.Content) return state
        return state.copy(subjects = state.subjects.map { subject ->
            subject.copy(
                expanded = subject.subjectId in subs,
                semesters = subject.semesters.map { it.copy(expanded = (subject.subjectId to it.number) in sems) },
            )
        })
    }

    fun toggleSubject(subjectId: Long) {
        if (subjectId in expandedSubjects.value) expandedSubjects.value = expandedSubjects.value - subjectId
        else openSubject(subjectId)
    }

    fun toggleSemester(subjectId: Long, number: Int) {
        val key = subjectId to number
        expandedSemesters.value =
            if (key in expandedSemesters.value) expandedSemesters.value - key else expandedSemesters.value + key
    }

    /** Homework/Behaviour pattern: VM owns the in-memory flip; pure write seam; guarded + idempotent. */
    fun markSeen(grade: GradeFull) {
        if (grade.seen) return
        grade.seen = true
        viewModelScope.launch(dispatcher) { onMarkSeen(grade) }
    }

    fun markAllSeen() {
        viewModelScope.launch(dispatcher) { onMarkAllSeen() }
    }

    /**
     * Pure derivation of the legacy onGradesEditorClick payload (GradesListFragment:153-172).
     * Faithful-intent port with two deliberate clean-ups: explicit null/0f weighted-vs-normal fallback guard,
     * and other-semester selected by number (`!= number`) for the K-12 {1,2} domain. Single-semester subjects
     * yield null other-* fields (matching the legacy null otherSemester).
     */
    fun editorArgs(subjectId: Long, number: Int): GradesEditorArgs? {
        val content = uiState.value as? GradesUiState.Content ?: return null
        val subject = content.subjects.firstOrNull { it.subjectId == subjectId } ?: return null
        val other = subject.semesters.firstOrNull { it.number != number }
        var sum = other?.averages?.normalWeightedSum
        var count = other?.averages?.normalWeightedCount
        if (sum == null || sum == 0f || count == null || count == 0f) {
            sum = other?.averages?.normalSum
            count = other?.averages?.normalCount?.toFloat()
        }
        return GradesEditorArgs(
            subjectId = subjectId,
            semester = number,
            averageMode = latestInputs.value?.yearAverageMode ?: return null,
            yearAverageBefore = subject.averages.normalAvg,
            gradeSumOtherSemester = sum,
            gradeCountOtherSemester = count,
            averageOtherSemester = other?.averages?.normalAvg,
            finalOtherSemester = other?.finalGrade?.value,
        )
    }

    /** Host-constructed (carries the deep-link subject id) — the only reader of App statics / gradesManager
     *  for the Android-free units (source/math/inputs); display/color seams are host-bound at the edge. */
    class Factory(
        private val appContext: Context,
        private val expandedSubjectInitial: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = appContext.applicationContext as App
            val m = app.gradesManager
            // Capture once: the flow must observe the SAME instances readGradesInputs reads, or a
            // profile switch leaves it listening to one profile and reading another.
            val profileConfig = app.profile.config
            val globalGrades = app.config.grades
            return GradesViewModel(
                // The ORDER BY clause is read once here and frozen for the ViewModel's life, so the
                // builder now owns SUBJECT order (GradesTreeBuilder.kt:115-120) and SQL owns the rest.
                // Outcome-identical for the two values the dialog can write: ORDER_BY_SUBJECT_ASC and
                // the ORDER_BY_DATE_DESC else-branch share the tail `gradeSemester DESC, addedDate
                // DESC` (GradesManager.kt:81, :84), which fixes semester order inside a subject and
                // grade order inside a semester. NOT identical for a profile stored at
                // ORDER_BY_DATE_ASC (2) — the dialog cannot write it, AppConfigMigrationV3.kt:47 can —
                // where the frozen clause keeps grades oldest-first after a sort switch until the
                // fragment is rebuilt. Accepted; no data loss, self-heals on the next navigation.
                source = { app.db.gradeDao().getAllOrderBy(App.profileId, m.getOrderByString()).asFlow() },
                math = Math(
                    gradeValue = { m.getGradeValue(it) },
                    // Live per grade, like gradeValue's plusValue/minusValue reads: a captured pair
                    // would freeze the two dontCount keys for the ViewModel's life.
                    gradeWeight = { m.getGradeWeight(m.dontCountEnabled, m.dontCountGrades, it) },
                    semesterAverage = { a -> m.calculateAverages(a, null) },
                    yearAverage = { a, inputs -> m.calculateAverages(a, inputs.map { adapt(it) }) },
                    roundedGrade = { m.getRoundedGrade(it) },
                ),
                inputs = configFlow(app.config, profileConfig) {
                    readGradesInputs(globalGrades, profileConfig, m.isUniversity, App.devMode)
                },
                expandedSubjectInitial = expandedSubjectInitial,
                onMarkAllSeen = { App.db.metadataDao().setAllSeen(App.profileId, MetadataType.GRADE, true) },
                onMarkSeen = { App.db.metadataDao().setSeen(App.profileId, it, true) },
            ) as T
        }

        /** Adapt the builder-owned SemesterAvgInput back to the legacy GradesSemester for GradesManager.calculateAverages. */
        private fun adapt(input: SemesterAvgInput): GradesSemester =
            GradesSemester(0L, input.number).also { sem ->
                sem.averages.normalAvg = input.normalAvg
                input.finalValue?.let { fv ->
                    sem.finalGrade = GradeFull(
                        profileId = 0, id = 0, name = "", type = 0, value = fv, weight = 0f, color = 0,
                        category = null, description = null, comment = null, semester = input.number, teacherId = 0, subjectId = 0,
                    )
                }
            }
    }
}
