/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.TeacherAbsenceFull
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val cfg = AgendaBuilder.Config(agendaLessonChanges = true, agendaTeacherAbsence = true)

    private fun event(id: Long, date: Date, seen: Boolean = true): EventFull = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.date } returns date
        every { eventColor } returns 0xFF2196F3.toInt()
        every { this@mockk.seen } returns seen
    }

    private fun absence(from: Date, to: Date): TeacherAbsenceFull =
        mockk(relaxed = true) {
            every { dateFrom } returns from
            every { dateTo } returns to
        }

    private fun vm(
        events: () -> Flow<List<EventFull>>,
        onMarkSeen: (EventFull) -> Unit = {},
        onMarkAllSeen: () -> Unit = {},
        changes: () -> Flow<List<LessonFull>> = { flowOf(emptyList()) },
        absences: () -> Flow<List<TeacherAbsenceFull>> = { flowOf(emptyList()) },
        configFlow: Flow<AgendaBuilder.Config> = flowOf(cfg),
    ) = AgendaViewModel(events, changes, absences, configFlow, Date(2026, 6, 1), onMarkAllSeen, onMarkSeen, dispatcher)

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `emits Content from sources`() = runTest(dispatcher) {
        val model = vm(events = { flowOf(listOf(event(1, Date(2026, 6, 1)))) })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is AgendaUiState.Content)
        job.cancel()
    }

    @Test
    fun `setSelectedDate refilters the day list, month dots unchanged`() = runTest(dispatcher) {
        val model = vm(events = { flowOf(listOf(event(1, Date(2026, 6, 1)), event(2, Date(2026, 6, 5)))) })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val before = model.uiState.value as AgendaUiState.Content
        assertEquals(1, before.dayItems.size)

        model.setSelectedDate(Date(2026, 6, 5))
        advanceUntilIdle()
        val after = model.uiState.value as AgendaUiState.Content
        assertEquals(1, after.dayItems.size)
        assertEquals(before.monthDots.keys, after.monthDots.keys)
        job.cancel()
    }

    @Test
    fun `markSeen is guarded and idempotent, dispatched off-main`() = runTest(dispatcher) {
        var calls = 0
        val model = vm(events = { flowOf(listOf(event(1, Date(2026, 6, 1)))) }, onMarkSeen = { calls++ })
        val unseen = event(99, Date(2026, 6, 1), seen = false)
        model.markSeen(unseen)
        model.markSeen(unseen)
        advanceUntilIdle()
        assertEquals(1, calls)
        model.markSeen(event(100, Date(2026, 6, 1), seen = true))
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `markAllSeen calls the seam off-main`() = runTest(dispatcher) {
        val onAll = mockk<() -> Unit>(relaxed = true)
        vm(events = { flowOf(listOf(event(1, Date(2026, 6, 1)))) }, onMarkAllSeen = onAll).markAllSeen()
        advanceUntilIdle()
        verify(exactly = 1) { onAll.invoke() }
    }

    @Test
    fun `source re-emit recomputes`() = runTest(dispatcher) {
        val src = MutableStateFlow(listOf(event(1, Date(2026, 6, 1))))
        val model = vm(events = { src })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, (model.uiState.value as AgendaUiState.Content).monthDots.size)
        src.value = listOf(event(1, Date(2026, 6, 1)), event(2, Date(2026, 6, 2)))
        advanceUntilIdle()
        assertEquals(2, (model.uiState.value as AgendaUiState.Content).monthDots.size)
        job.cancel()
    }

    @Test
    fun `selecting a day with no events yields empty dayItems but keeps the calendar`() = runTest(dispatcher) {
        val model = vm(events = { flowOf(listOf(event(1, Date(2026, 6, 1)))) })
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.setSelectedDate(Date(2026, 6, 2))   // no events on that day
        advanceUntilIdle()
        val state = model.uiState.value as AgendaUiState.Content
        assertTrue(state.dayItems.isEmpty())                       // empty selected day
        assertTrue(state.monthDots.containsKey(Date(2026, 6, 1)))  // calendar still shows the event day
        job.cancel()
    }

    /**
     * The starvation guard, as a transition. `combine` produces nothing until every input has emitted,
     * so an unprimed config flow holds the screen on Loading. Drop `configFlow` from the combine and
     * the first assert fails; the second proves the first is not passing because the VM never reaches
     * Content at all.
     */
    @Test
    fun `stays Loading until the config flow emits, then reaches Content`() = runTest(dispatcher) {
        val configs = MutableSharedFlow<AgendaBuilder.Config>(extraBufferCapacity = 1)
        val model = vm(events = { flowOf(listOf(event(1, Date(2026, 6, 1)))) }, configFlow = configs)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is AgendaUiState.Loading)

        configs.tryEmit(cfg)
        advanceUntilIdle()
        assertTrue(model.uiState.value is AgendaUiState.Content)
        job.cancel()
    }

    /**
     * The point of the phase: turning `agendaTeacherAbsence` off re-derives with no new source data
     * and no ViewModel recreation. The absence row is fed unconditionally — exactly as the Factory now
     * does it — so the ONLY thing removing its item is the config flag (AgendaBuilder.kt:72).
     */
    @Test
    fun `re-derives when the config flow emits a new value`() = runTest(dispatcher) {
        val day = Date(2026, 6, 1)
        val configs = MutableStateFlow(cfg)   // both flags true
        val model = vm(
            // The event keeps `acc` non-empty once the absence flag goes off. Without it
            // AgendaBuilder returns AgendaUiState.Empty (AgendaBuilder.kt:63) and the
            // `as Content` cast in dayItems() throws ClassCastException.
            events = { flowOf(listOf(event(1, day))) },
            absences = { flowOf(listOf(absence(day, day))) },
            configFlow = configs,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, dayItems(model).filterIsInstance<AgendaItem.TeacherAbsenceItem>().size)

        configs.value = cfg.copy(agendaTeacherAbsence = false)
        advanceUntilIdle()
        assertEquals(0, dayItems(model).filterIsInstance<AgendaItem.TeacherAbsenceItem>().size)
        assertEquals(1, dayItems(model).filterIsInstance<AgendaItem.EventItem>().size)
        job.cancel()
    }

    private fun dayItems(model: AgendaViewModel): List<AgendaItem> =
        (model.uiState.value as AgendaUiState.Content).dayItems
}
