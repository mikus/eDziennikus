/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.ui.grades.GradesTreeBuilder.Config
import eu.mikus.edziennik.ui.grades.GradesTreeBuilder.Math
import eu.mikus.edziennik.ui.grades.models.GradesAverages
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val math = Math(
        gradeValue = { it.value }, gradeWeight = { it.weight },
        semesterAverage = { a: GradesAverages -> if (a.normalWeightedCount > 0f) a.normalAvg = a.normalWeightedSum / a.normalWeightedCount },
        yearAverage = { a: GradesAverages, sems -> sems.mapNotNull { it.normalAvg }.takeIf { it.isNotEmpty() }?.let { a.normalAvg = it.average().toFloat() } },
        roundedGrade = { v -> v.toInt() + if (v % 1f >= 0.75f) 1 else 0 },
    )
    private val config = Config(false, false, false, false, 0)

    private fun gradesInputs(
        config: Config = this.config,
        plusValue: Float? = null,
        yearAverageMode: Int = 0,
    ) = GradesInputs(
        config = config,
        plusValue = plusValue,
        minusValue = null,
        averageWithoutWeight = true,
        yearAverageMode = yearAverageMode,
        dontCountEnabled = false,
        dontCountGrades = emptyList(),
    )

    private fun grade(id: Long, subjectId: Long, semester: Int = 1, value: Float = 4f, seen: Boolean = true): GradeFull =
        mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.subjectId } returns subjectId
            every { subjectLongName } returns "Subject $subjectId"
            every { this@mockk.semester } returns semester
            every { type } returns Grade.TYPE_NORMAL
            every { this@mockk.value } returns value
            every { weight } returns 1f
            every { name } returns "4"
            every { this@mockk.seen } returns seen
            every { this@mockk.seen = any() } returns Unit
            every { this@mockk.showAsUnseen = any() } returns Unit
            every { isImproved } returns false
            every { addedDate } returns id
        }

    private fun vm(
        grades: List<GradeFull>,
        marked: MutableList<GradeFull> = mutableListOf(),
        markedAll: MutableList<Unit> = mutableListOf(),
        initialSubject: Long = 0L,
        math: Math = this.math,
        inputs: Flow<GradesInputs> = flowOf(gradesInputs()),
    ) = GradesViewModel(
        source = { flowOf(grades) as Flow<List<GradeFull>> },
        math = math,
        inputs = inputs,
        expandedSubjectInitial = initialSubject,
        onMarkAllSeen = { markedAll.add(Unit) },
        onMarkSeen = { marked.add(it) },
        dispatcher = dispatcher,
    )

    private fun semester(model: GradesViewModel) =
        (model.uiState.value as GradesUiState.Content).subjects.single().semesters.single()

    @Test fun `emits Content from the builder`() = runTest(dispatcher) {
        val model = vm(listOf(grade(1, 10)))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is GradesUiState.Content)
        job.cancel()
    }

    @Test fun `toggleSubject open adds subject and its first semester`() = runTest(dispatcher) {
        val model = vm(listOf(grade(1, 10, semester = 1)))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.toggleSubject(10L)
        advanceUntilIdle()
        val content = model.uiState.value as GradesUiState.Content
        val subject = content.subjects.single { it.subjectId == 10L }
        assertTrue(subject.expanded)
        assertTrue(subject.semesters.single { it.number == 1 }.expanded)
        model.toggleSubject(10L)
        advanceUntilIdle()
        assertTrue((model.uiState.value as GradesUiState.Content).subjects.single().expanded.not())
        job.cancel()
    }

    @Test fun `markSeen flips seen and fires the seam once, idempotent`() = runTest(dispatcher) {
        val marked = mutableListOf<GradeFull>()
        val g = grade(1, 10, seen = false)
        val seenState = booleanArrayOf(false)
        every { g.seen } answers { seenState[0] }
        every { g.seen = any() } answers { seenState[0] = firstArg() }
        val model = vm(listOf(g), marked = marked)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.markSeen(g); advanceUntilIdle()
        model.markSeen(g); advanceUntilIdle()
        assertEquals(1, marked.size)
        assertTrue(seenState[0])
        job.cancel()
    }

    @Test fun `markAllSeen calls the seam`() = runTest(dispatcher) {
        val all = mutableListOf<Unit>()
        val model = vm(listOf(grade(1, 10)), markedAll = all)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.markAllSeen(); advanceUntilIdle()
        assertEquals(1, all.size)
        job.cancel()
    }

    @Test fun `deep-link seed expands the target subject and its first semester`() = runTest(dispatcher) {
        val model = vm(listOf(grade(1, 10, semester = 1)), initialSubject = 10L)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val subject = (model.uiState.value as GradesUiState.Content).subjects.single { it.subjectId == 10L }
        assertTrue(subject.expanded)
        assertTrue(subject.semesters.single { it.number == 1 }.expanded)
        job.cancel()
    }

    @Test fun `deep-link to an absent subject is a no-op`() = runTest(dispatcher) {
        val model = vm(listOf(grade(1, 10)), initialSubject = 999L)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue((model.uiState.value as GradesUiState.Content).subjects.none { it.expanded })
        job.cancel()
    }

    @Test fun `editorArgs reproduces other-semester payload, null when not Content`() = runTest(dispatcher) {
        val model = vm(listOf(grade(1, 10, semester = 1), grade(2, 10, semester = 2)))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val args = model.editorArgs(10L, 1)!!
        assertEquals(10L, args.subjectId)
        assertEquals(1, args.semester)
        assertEquals(4f, args.gradeSumOtherSemester)
        assertEquals(1f, args.gradeCountOtherSemester)
        job.cancel()
    }

    @Test fun `editorArgs with a single-semester subject has null other-semester fields`() = runTest(dispatcher) {
        val model = vm(listOf(grade(1, 10, semester = 1)))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val args = model.editorArgs(10L, 1)!!
        assertEquals(null, args.gradeSumOtherSemester)
        assertEquals(null, args.averageOtherSemester)
        assertEquals(null, model.editorArgs(999L, 1))
        job.cancel()
    }

    /**
     * The phase, in one test. The two assertions gate two different halves and two different mutations:
     * - `plusValue` never reaches the builder; `GradesManager.getGradeValue` re-reads it at call time
     *   (GradesManager.kt:113-127), which the math stub below mirrors. So the ONLY thing that can
     *   apply a new value is the combine re-running — that is what the fourth input buys, and what a
     *   ViewModel rebuild used to do.
     * - `hideImproved` does reach the builder through `i.config`. Freeze that (build from the first
     *   emission forever) and this second assertion goes red while the first still passes.
     */
    @Test fun `re-derives when the inputs flow emits a new value`() = runTest(dispatcher) {
        val inputs = MutableStateFlow(gradesInputs(plusValue = 0f))
        val liveMath = Math(
            gradeValue = { it.value + (inputs.value.plusValue ?: 0f) },
            gradeWeight = { it.weight },
            semesterAverage = { a: GradesAverages -> if (a.normalWeightedCount > 0f) a.normalAvg = a.normalWeightedSum / a.normalWeightedCount },
            yearAverage = { _, _ -> },
            roundedGrade = { v -> v.toInt() },
        )
        val improved = grade(2, 10).also { every { it.isImproved } returns true }
        val model = vm(listOf(grade(1, 10), improved), math = liveMath, inputs = inputs)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(4f, semester(model).averages.normalAvg)
        assertEquals(2, semester(model).grades.size)

        inputs.value = gradesInputs(config = config.copy(hideImproved = true), plusValue = 1f)
        advanceUntilIdle()
        assertEquals(5f, semester(model).averages.normalAvg)   // live plusValue, applied by re-deriving
        assertEquals(1, semester(model).grades.size)           // i.config, actually re-read
        job.cancel()
    }

    /**
     * The starvation guard, as a transition. `combine` produces nothing until every input has emitted,
     * so an unprimed inputs flow holds the screen on Loading forever — which is why `configFlow`
     * primes with `onStart` (ConfigFlow.kt:26). The second assert proves the first is not passing
     * because the VM never reaches Content at all.
     */
    @Test fun `stays Loading until the inputs flow emits, then reaches Content`() = runTest(dispatcher) {
        val inputs = MutableSharedFlow<GradesInputs>(extraBufferCapacity = 1)
        val model = vm(listOf(grade(1, 10)), inputs = inputs)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is GradesUiState.Loading)

        inputs.tryEmit(gradesInputs())
        advanceUntilIdle()
        assertTrue(model.uiState.value is GradesUiState.Content)
        job.cancel()
    }

    /**
     * The regression this phase would otherwise introduce. `averageMode` does not feed the tree — it
     * goes into the GRADES_EDITOR nav Bundle (GradesListFragment.onEditorClick), where
     * GradesEditorViewModel.kt:88 computes `yearAverageAfter` from it. Today a dismiss rebuilds the
     * fragment and refreshes it; after this phase nothing does, so it has to come off the flow.
     * Mutation: capture it as a ctor field again and this goes red.
     */
    @Test fun `editorArgs averageMode tracks the newest yearAverageMode`() = runTest(dispatcher) {
        val inputs = MutableStateFlow(gradesInputs(yearAverageMode = 4))
        val model = vm(listOf(grade(1, 10, semester = 1)), inputs = inputs)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(4, model.editorArgs(10L, 1)!!.averageMode)

        inputs.value = gradesInputs(yearAverageMode = 1)
        advanceUntilIdle()
        assertEquals(1, model.editorArgs(10L, 1)!!.averageMode)
        job.cancel()
    }
}
