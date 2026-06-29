/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.entity.AttendanceType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val absentType = AttendanceType(1, 1, Attendance.TYPE_ABSENT, "nieobecność", "nb", "nb", null)

    private fun row(id: Long, semester: Int = 1, seen: Boolean = true): AttendanceFull =
        mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.semester } returns semester
            every { this@mockk.seen } returns seen
            every { baseType } returns Attendance.TYPE_ABSENT
            every { typeObject } returns absentType
            every { subjectId } returns 1L
            every { subjectLongName } returns "Algebra"
            every { date } returns eu.mikus.edziennik.utils.models.Date(2026, 6, 1)
        }

    private val config = AttendanceTreeBuilder.Config(
        groupConsecutiveDays = false, showPresenceInMonth = false, currentSemester = 1,
    )

    private fun vm(
        source: () -> Flow<List<AttendanceFull>>,
        onMarkSeen: (AttendanceFull) -> Unit = {},
        onMarkAllSeen: () -> Unit = {},
    ) = AttendanceViewModel(source, config, Period.ALL, onMarkAllSeen, onMarkSeen, dispatcher)

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `emits Content from the source`() = runTest(dispatcher) {
        val model = vm(source = { flowOf(listOf(row(1))) })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is AttendanceUiState.Content)
        job.cancel()
    }

    @Test
    fun `setPeriod rebuilds the Summary tab only`() = runTest(dispatcher) {
        val model = vm(source = { flowOf(listOf(row(1, semester = 1), row(2, semester = 2))) })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val before = model.uiState.value as AttendanceUiState.Content
        val listBefore = before.tabs.filterIsInstance<AttendanceTab.ListTab>().single()

        model.setPeriod(Period.SEM1)
        advanceUntilIdle()
        val after = model.uiState.value as AttendanceUiState.Content
        val summary = after.tabs.filterIsInstance<AttendanceTab.SummaryTab>().single()
        val listAfter = after.tabs.filterIsInstance<AttendanceTab.ListTab>().single()

        assertEquals(1, summary.subjects.single().leaves.size)   // SEM1 keeps only the semester-1 row
        assertEquals(listBefore.leaves.size, listAfter.leaves.size) // list tab unaffected by period
        job.cancel()
    }

    @Test
    fun `toggleNode flips expand without rebuilding from rows`() = runTest(dispatcher) {
        val model = vm(source = { flowOf(listOf(row(1))) })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val key = (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.SummaryTab>().single().subjects.single().key

        model.toggleNode(key)
        advanceUntilIdle()
        val expanded = (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.SummaryTab>().single().subjects.single()
        assertTrue(expanded.expanded)

        model.toggleNode(key)
        advanceUntilIdle()
        val collapsed = (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.SummaryTab>().single().subjects.single()
        assertFalse(collapsed.expanded)
        job.cancel()
    }

    @Test
    fun `markSeen is guarded and idempotent`() = runTest(dispatcher) {
        var calls = 0
        val model = vm(source = { flowOf(listOf(row(1))) }, onMarkSeen = { calls++ })
        val unseen = row(99, seen = false)

        model.markSeen(unseen)
        model.markSeen(unseen)   // already in the seen set -> no second launch
        advanceUntilIdle()       // the write is dispatched off-main, so drain it before asserting
        assertEquals(1, calls)

        model.markSeen(row(100, seen = true))  // already seen -> no launch
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `markAllSeen calls the seam`() = runTest(dispatcher) {
        val onAll = mockk<() -> Unit>(relaxed = true)
        vm(source = { flowOf(listOf(row(1))) }, onMarkAllSeen = onAll).markAllSeen()
        advanceUntilIdle()       // the bulk write is dispatched off-main
        verify(exactly = 1) { onAll.invoke() }
    }

    @Test
    fun `toggleNode expands a non-summary header (Months)`() = runTest(dispatcher) {
        val model = vm(source = { flowOf(listOf(row(1))) })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val monthKey = (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.MonthsTab>().single().months.single().key

        model.toggleNode(monthKey)
        advanceUntilIdle()
        val month = (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.MonthsTab>().single().months.single()
        assertTrue(month.expanded)               // withExpanded re-marks the Months branch, not just Summary
        job.cancel()
    }

    @Test
    fun `expanded node stays expanded across a source re-emit`() = runTest(dispatcher) {
        val source = MutableStateFlow(listOf(row(1)))
        val model = vm(source = { source })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val key = (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.SummaryTab>().single().subjects.single().key
        model.toggleNode(key)
        advanceUntilIdle()

        source.value = listOf(row(1), row(2))    // re-emit: builder mints fresh headers with expanded=false
        advanceUntilIdle()
        val subject = (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.SummaryTab>().single().subjects.single()
        assertTrue(subject.expanded)             // expansion re-applied by NodeKey value-equality
        job.cancel()
    }

    @Test
    fun `state recomputes when the source emits again`() = runTest(dispatcher) {
        val source = MutableStateFlow(listOf(row(1)))
        val model = vm(source = { source })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.ListTab>().single().leaves.size)

        source.value = listOf(row(1), row(2))
        advanceUntilIdle()
        assertEquals(2, (model.uiState.value as AttendanceUiState.Content)
            .tabs.filterIsInstance<AttendanceTab.ListTab>().single().leaves.size)
        job.cancel()
    }
}
