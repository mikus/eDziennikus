/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val date = Date(2026, 6, 1)
    private val cfg = TimetableDayBuilder.Config(false, true, true, 6, 19)

    private fun lesson(type: Int = Lesson.TYPE_NORMAL, seen: Boolean = true): LessonFull = mockk(relaxed = true) {
        every { this@mockk.type } returns type
        every { displayStartTime } returns Time(8, 0, 0)
        every { displayEndTime } returns Time(8, 45, 0)
        every { this@mockk.startTime } returns Time(8, 0, 0)
        every { this@mockk.seen } returns seen
        every { this@mockk.id } returns 42L
    }

    private fun vm(
        lessons: (Date) -> Flow<List<LessonFull>>,
        onMarkSeen: (LessonFull) -> Unit = {},
        configFlow: Flow<TimetableDayBuilder.Config> = flowOf(cfg),
    ) = TimetableViewModel(
        lessonsSource = lessons,
        eventsFetch = { emptyList<EventFull>() },
        attendanceFetch = { emptyList<AttendanceFull>() },
        configFlow = configFlow,
        initialDate = date,
        onMarkSeen = onMarkSeen,
        dispatcher = dispatcher,
    )

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `dayFlow emits Content built from the day sources`() = runTest(dispatcher) {
        val model = vm(lessons = { flowOf(listOf(lesson())) })
        val flow = model.dayFlow(date)
        var state: TimetableDayUiState = TimetableDayUiState.Loading
        val job = launch { flow.collect { state = it } }
        advanceUntilIdle()
        assertIs<TimetableDayUiState.Content>(state)
        job.cancel()
    }

    @Test
    fun `dayFlow with no lessons emits NoTimetable`() = runTest(dispatcher) {
        val model = vm(lessons = { flowOf(emptyList()) })
        var state: TimetableDayUiState = TimetableDayUiState.Loading
        val job = launch { model.dayFlow(date).collect { state = it } }
        advanceUntilIdle()
        assertIs<TimetableDayUiState.NoTimetable>(state)
        job.cancel()
    }

    @Test
    fun `markSeen is guarded, idempotent, and only for non-normal unseen`() = runTest(dispatcher) {
        var calls = 0
        val model = vm(lessons = { flowOf(listOf(lesson())) }, onMarkSeen = { calls++ })
        val changeUnseen: LessonFull = mockk(relaxed = true) {
            every { type } returns Lesson.TYPE_CHANGE
            every { seen } returns false
            every { id } returns 7L
        }
        model.markSeen(changeUnseen)
        model.markSeen(changeUnseen)   // idempotent
        advanceUntilIdle()
        assertEquals(1, calls)

        val normalUnseen: LessonFull = mockk(relaxed = true) {
            every { type } returns Lesson.TYPE_NORMAL
            every { seen } returns false
            every { id } returns 8L
        }
        model.markSeen(normalUnseen)   // normal -> skipped
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `requestDate publishes and clearRequestedDate resets it`() = runTest(dispatcher) {
        val model = vm(lessons = { flowOf(listOf(lesson())) })
        model.requestDate(Date(2026, 6, 10))
        assertEquals(Date(2026, 6, 10).value, model.requestedDate.value?.value)
        model.clearRequestedDate()
        assertEquals(null, model.requestedDate.value)
    }

    @Test
    fun `onPageChanged updates currentDate`() = runTest(dispatcher) {
        val model = vm(lessons = { flowOf(listOf(lesson())) })
        model.onPageChanged(Date(2026, 6, 12))
        assertEquals(Date(2026, 6, 12).value, model.currentDate.value.value)
    }

    /**
     * The phase: a config change re-derives the day with no new lesson data and no ViewModel
     * recreation. `trimHourRange` is the observable because it is the only Config flag the builder
     * turns into a field of Content without an events or attendance fixture — vm() feeds both as
     * emptyList(), so showEvents/showAttendance are unobservable here.
     */
    @Test
    fun `dayFlow re-emits a new hour range when the config flow emits`() = runTest(dispatcher) {
        val configs = MutableStateFlow(cfg)                // trimHourRange = false
        val model = vm(lessons = { flowOf(listOf(lesson())) }, configFlow = configs)
        val states = mutableListOf<TimetableDayUiState>()
        val job = launch { model.dayFlow(date).collect { states += it } }
        advanceUntilIdle()
        assertEquals(6, assertIs<TimetableDayUiState.Content>(states.last()).startHour)

        configs.value = cfg.copy(trimHourRange = true)
        advanceUntilIdle()
        assertEquals(2, states.size)
        assertEquals(8, assertIs<TimetableDayUiState.Content>(states.last()).startHour)
        job.cancel()
    }

    /**
     * The starvation guard, the AgendaViewModelTest shape. `combine` produces nothing until every
     * input has emitted, so an unprimed config flow yields no day at all — which is why configFlow()
     * primes with .onStart. Gate: prime the input inside dayFlow (see the mutation table). Deleting
     * .onStart from ConfigFlow.kt CANNOT redden this test, which injects its own flow.
     */
    @Test
    fun `dayFlow emits nothing until the config flow emits`() = runTest(dispatcher) {
        val configs = MutableSharedFlow<TimetableDayBuilder.Config>(extraBufferCapacity = 1)
        val model = vm(lessons = { flowOf(listOf(lesson())) }, configFlow = configs)
        val states = mutableListOf<TimetableDayUiState>()
        val job = launch { model.dayFlow(date).collect { states += it } }
        advanceUntilIdle()
        assertEquals(0, states.size)

        configs.tryEmit(cfg)
        advanceUntilIdle()
        assertEquals(1, states.size)
        job.cancel()
    }
}
