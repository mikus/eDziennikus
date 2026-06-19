/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.teachers

import eu.mikus.edziennik.data.db.entity.Teacher
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeachersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun teacher(tid: Long, login: String?, subj: MutableList<Long>, roleType: Int): Teacher =
        mockk(relaxed = true) {
            every { id } returns tid
            every { loginId } returns login
            every { subjects } returns subj
            every { type } returns roleType
        }

    @Test
    fun `empty source yields Empty`() = runTest(dispatcher) {
        val vm = TeachersViewModel(
            source = { flowOf(emptyList()) },
            subjects = { emptyList() },
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(TeachersUiState.Empty, vm.uiState.value)
        job.cancel()
    }

    @Test
    fun `non-empty source yields Content with canSendMessage from loginId`() = runTest(dispatcher) {
        val withLogin = teacher(1, "abc", mutableListOf(10L), roleType = 1)
        val withoutLogin = teacher(2, null, mutableListOf(10L), roleType = 1)
        val vm = TeachersViewModel(
            source = { flowOf(listOf(withLogin, withoutLogin)) },
            subjects = { emptyList() },
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is TeachersUiState.Content)
        assertEquals(2, state.rows.size)
        assertTrue(state.rows.first { it.teacher.id == 1L }.canSendMessage)
        assertFalse(state.rows.first { it.teacher.id == 2L }.canSendMessage)
        job.cancel()
    }

    @Test
    fun `teachers with subjects sort before those without`() = runTest(dispatcher) {
        val noSubjects = teacher(1, "a", mutableListOf(), roleType = 1)
        val hasSubjects = teacher(2, "a", mutableListOf(10L), roleType = 1)
        val vm = TeachersViewModel(
            source = { flowOf(listOf(noSubjects, hasSubjects)) },
            subjects = { emptyList() },
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value as TeachersUiState.Content
        assertEquals(2L, state.rows.first().teacher.id)
        job.cancel()
    }

    @Test
    fun `among teachers with subjects, real roles sort before type-zero`() = runTest(dispatcher) {
        val typeZero = teacher(1, "a", mutableListOf(10L), roleType = 0)
        val realRole = teacher(2, "a", mutableListOf(10L), roleType = 1)
        val vm = TeachersViewModel(
            source = { flowOf(listOf(typeZero, realRole)) },
            subjects = { emptyList() },
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value as TeachersUiState.Content
        assertEquals(2L, state.rows.first().teacher.id)
        job.cancel()
    }
}
