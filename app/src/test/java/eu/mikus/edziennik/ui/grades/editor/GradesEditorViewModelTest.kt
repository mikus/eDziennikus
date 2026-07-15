/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades.editor

import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.entity.Subject
import eu.mikus.edziennik.ui.grades.GradesEditorArgs
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_ALL_GRADES
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class GradesEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun grade(id: Long, value: Float, weight: Float = 1f): Grade = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.name } returns value.toInt().toString()
        every { this@mockk.value } returns value
        every { this@mockk.weight } returns weight
        every { this@mockk.type } returns Grade.TYPE_NORMAL
        every { this@mockk.semester } returns 1
        every { this@mockk.description } returns "d"
        every { this@mockk.category } returns "c"
    }

    // Subject is a Java entity with public fields (id, longName) — not mockable via every{}; set them directly.
    private fun subject(name: String = "Matematyka"): Subject = mockk<Subject>(relaxed = true).apply {
        id = 7L
        longName = name
    }

    private fun args(averageMode: Int = YEAR_ALL_GRADES) = GradesEditorArgs(
        subjectId = 7L, semester = 1, averageMode = averageMode, yearAverageBefore = 4.2f,
        gradeSumOtherSemester = 8f, gradeCountOtherSemester = 2f, averageOtherSemester = 4f, finalOtherSemester = 4f,
    )

    private fun vm(
        subject: Subject? = subject(),
        grades: List<Grade> = listOf(grade(1, 5f), grade(2, 3f)),
        args: GradesEditorArgs = args(),
    ) = GradesEditorViewModel(
        loadSubject = { subject },
        loadGrades = { grades },
        args = args,
        dontCount = DontCountConfig(false, emptySet()),
        dispatcher = dispatcher,
    )

    @Test
    fun `load emits Content with the before averages`() = runTest(dispatcher) {
        val model = vm()
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value as GradesEditorUiState.Content
        assertEquals("Matematyka", state.subjectName)
        assertEquals(4f, state.averageBefore)     // (5+3)/2
        assertEquals(4f, state.averageAfter)
        assertEquals(true, state.yearAverageVisible)
        job.cancel()
    }

    @Test
    fun `add recomputes averageAfter, restore resets it`() = runTest(dispatcher) {
        val model = vm()
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.add(EditorGrade(99, "1", 1f, "new", 1f))
        advanceUntilIdle()
        assertEquals(3f, (model.uiState.value as GradesEditorUiState.Content).averageAfter)  // (5+3+1)/3
        model.restore()
        advanceUntilIdle()
        assertEquals(4f, (model.uiState.value as GradesEditorUiState.Content).averageAfter)
        job.cancel()
    }

    @Test
    fun `edit changes a grade's value then weight, recomputing averageAfter`() = runTest(dispatcher) {
        val model = vm()
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.edit(1, name = "1", value = 1f)                              // the "5" becomes "1"
        advanceUntilIdle()
        assertEquals(2f, (model.uiState.value as GradesEditorUiState.Content).averageAfter)   // (1+3)/2
        model.edit(2, weight = 3f)                                         // the "3" gets weight 3
        advanceUntilIdle()
        assertEquals((1f + 9f) / 4f, (model.uiState.value as GradesEditorUiState.Content).averageAfter)  // (1*1 + 3*3)/(1+3)
        job.cancel()
    }

    @Test
    fun `remove recomputes averageAfter`() = runTest(dispatcher) {
        val model = vm()
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.remove(2)                                                     // drop the "3"
        advanceUntilIdle()
        assertEquals(5f, (model.uiState.value as GradesEditorUiState.Content).averageAfter)
        job.cancel()
    }

    @Test
    fun `missing subject yields SubjectMissing`() = runTest(dispatcher) {
        val model = vm(subject = null)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertIs<GradesEditorUiState.SubjectMissing>(model.uiState.value)
        job.cancel()
    }
}
