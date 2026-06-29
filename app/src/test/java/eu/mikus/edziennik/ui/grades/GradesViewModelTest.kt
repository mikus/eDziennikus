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
    ) = GradesViewModel(
        source = { flowOf(grades) as Flow<List<GradeFull>> },
        math = math,
        config = config,
        averageMode = 0,
        expandedSubjectInitial = initialSubject,
        onMarkAllSeen = { markedAll.add(Unit) },
        onMarkSeen = { marked.add(it) },
        dispatcher = dispatcher,
    )

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
}
