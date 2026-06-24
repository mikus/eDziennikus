/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.homework

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.utils.models.Date
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeworkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val todayDate = Date(2026, 6, 15)

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun event(
        eventId: Long,
        at: Date,
        done: Boolean = false,
        added: Long = 0L,
        isSeen: Boolean = true,
        timeValue: Int? = null,
        keywords: List<List<String?>> = listOf(listOf("event$eventId")),
    ): EventFull = mockk(relaxed = true) {
        every { id } returns eventId
        every { date } returns at
        every { isDone } returns done
        every { addedDate } returns added
        every { seen } returns isSeen
        every { time } returns timeValue?.let { tv -> mockk(relaxed = true) { every { value } returns tv } }
        every { notes } returns mutableListOf()
        every { searchKeywords } returns keywords
    }

    private fun vm(events: List<EventFull>, onMarkSeen: (EventFull) -> Unit = {}) =
        HomeworkViewModel(
            source = { flowOf(events) },
            today = { todayDate },
            onMarkSeen = onMarkSeen,
            dispatcher = dispatcher,
        )

    @Test
    fun `partitions current vs past by date and isDone`() = runTest(dispatcher) {
        val futureNotDone = event(1, at = Date(2026, 6, 20), done = false)
        val todayNotDone = event(2, at = Date(2026, 6, 15), done = false)
        val pastEvent = event(3, at = Date(2026, 6, 10), done = false)
        val todayDone = event(4, at = Date(2026, 6, 15), done = true)
        val futureDone = event(5, at = Date(2026, 6, 20), done = true)
        val model = vm(listOf(futureNotDone, todayNotDone, pastEvent, todayDone, futureDone))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is HomeworkUiState.Content)
        assertEquals(setOf(1L, 2L), state.current.map { it.event.id }.toSet())
        assertEquals(setOf(3L, 4L, 5L), state.past.map { it.event.id }.toSet())
        job.cancel()
    }

    @Test
    fun `blank query orders Current ascending and Past descending by date`() = runTest(dispatcher) {
        val c1 = event(1, at = Date(2026, 6, 18))
        val c2 = event(2, at = Date(2026, 6, 16))
        val p1 = event(3, at = Date(2026, 6, 10), done = true)
        val p2 = event(4, at = Date(2026, 6, 12), done = true)
        val model = vm(listOf(c1, c2, p1, p2))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value as HomeworkUiState.Content
        assertEquals(listOf(2L, 1L), state.current.map { it.event.id })
        assertEquals(listOf(4L, 3L), state.past.map { it.event.id })
        job.cancel()
    }

    @Test
    fun `search keeps matches best-first, drops non-matches, relevance not inverted on Past`() = runTest(dispatcher) {
        val better = event(1, at = Date(2026, 6, 10), done = true, keywords = listOf(listOf("Matematyka")))
        val worse = event(2, at = Date(2026, 6, 12), done = true, keywords = listOf(listOf("xMatematyka")))
        val noMatch = event(3, at = Date(2026, 6, 11), done = true, keywords = listOf(listOf("Fizyka")))
        val model = vm(listOf(better, worse, noMatch))
        val job = launch { model.uiState.collect {} }
        model.setQuery("mat")
        advanceUntilIdle()
        val state = model.uiState.value as HomeworkUiState.Content
        assertEquals(listOf(1L, 2L), state.past.map { it.event.id })
        assertTrue(state.current.isEmpty())
        job.cancel()
    }

    @Test
    fun `search Past equal-relevance sorts newer date first`() = runTest(dispatcher) {
        val older = event(1, at = Date(2026, 6, 10), done = true, keywords = listOf(listOf("Matematyka")))
        val newer = event(2, at = Date(2026, 6, 14), done = true, keywords = listOf(listOf("Matematyka")))
        val model = vm(listOf(older, newer))
        val job = launch { model.uiState.collect {} }
        model.setQuery("mat")
        advanceUntilIdle()
        val state = model.uiState.value as HomeworkUiState.Content
        assertEquals(listOf(2L, 1L), state.past.map { it.event.id })
        job.cancel()
    }

    @Test
    fun `unseen flag captured from seen state at classify time`() = runTest(dispatcher) {
        val unseenEvent = event(1, at = Date(2026, 6, 20), isSeen = false)
        val seenEvent = event(2, at = Date(2026, 6, 21), isSeen = true)
        val model = vm(listOf(unseenEvent, seenEvent))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value as HomeworkUiState.Content
        assertEquals(true, state.current.first { it.event.id == 1L }.unseen)
        assertEquals(false, state.current.first { it.event.id == 2L }.unseen)
        job.cancel()
    }

    @Test
    fun `markSeen flips once then is idempotent, and no-ops an already-seen event`() = runTest(dispatcher) {
        val marked = mutableListOf<EventFull>()
        // Backing-var `seen` so the in-memory flip is observable: a 2nd markSeen on the now-seen event
        // must be a no-op — the idempotency the row's onAppear LaunchedEffect relies on.
        var unseenFlag = false
        val unseenEvent = mockk<EventFull>(relaxed = true) {
            every { seen } answers { unseenFlag }
            every { seen = any() } answers { unseenFlag = true }
        }
        val seenEvent = event(2, at = Date(2026, 6, 20), isSeen = true)
        val model = vm(listOf(unseenEvent, seenEvent), onMarkSeen = { marked.add(it) })
        model.markSeen(unseenEvent)   // unseen -> flips + writes
        model.markSeen(unseenEvent)   // now seen -> no-op (idempotent)
        model.markSeen(seenEvent)     // already seen -> no-op
        advanceUntilIdle()
        assertEquals(listOf(unseenEvent), marked)            // exactly one write total
        verify(exactly = 1) { unseenEvent.seen = true }
        verify(exactly = 0) { seenEvent.seen = true }
    }

    @Test
    fun `empty source yields Content with empty tabs`() = runTest(dispatcher) {
        val model = vm(emptyList())
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value as HomeworkUiState.Content
        assertTrue(state.current.isEmpty() && state.past.isEmpty())
        job.cancel()
    }

    @Test
    fun `initial state is Loading then Content on first emission`() = runTest(dispatcher) {
        val model = vm(listOf(event(1, at = Date(2026, 6, 20))))
        assertEquals(HomeworkUiState.Loading, model.uiState.value)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is HomeworkUiState.Content)
        job.cancel()
    }
}
