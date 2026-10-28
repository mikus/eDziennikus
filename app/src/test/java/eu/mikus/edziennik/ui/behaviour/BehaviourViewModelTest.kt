/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.behaviour

import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.data.db.full.NoticeFull
import eu.mikus.edziennik.utils.models.Date
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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BehaviourViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // A fixed real date by default, so a notice is dated unless a test says otherwise and no test
    // depends on the clock (NoticeFull's own default is "now").
    private fun notice(
        id: Long,
        type: Int,
        semester: Int,
        seen: Boolean = false,
        addedDate: Long = Date(2026, 3, 10).inMillis,
    ): NoticeFull =
        NoticeFull(
            profileId = 1, id = id, type = type, semester = semester,
            text = "t", category = null, points = null, teacherId = 0, addedDate = addedDate,
        ).also { it.seen = seen }

    // One undated notice stored in each semester, so a filter that drops either one from the
    // semester it is not stored in fails.
    private val mixedSemesters = listOf(
        notice(1, Notice.TYPE_POSITIVE, 1),
        notice(2, Notice.TYPE_NEGATIVE, 2),
        notice(3, Notice.TYPE_NEGATIVE, 2, addedDate = Notice.ADDED_DATE_UNKNOWN),
        notice(4, Notice.TYPE_NEUTRAL, 1, addedDate = Notice.ADDED_DATE_UNKNOWN),
    )

    @Test
    fun `year keeps all, semester filter keeps only that semester`() = runTest(dispatcher) {
        val notices = listOf(notice(1, Notice.TYPE_POSITIVE, 1), notice(2, Notice.TYPE_NEGATIVE, 2))
        val vm = BehaviourViewModel(
            source = { flowOf(notices) },
            onMarkSeen = mockk(relaxed = true),
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as BehaviourUiState.Content).notices.size)

        vm.setFilter(SemesterFilter.SEMESTER_1)
        advanceUntilIdle()
        val content = vm.uiState.value as BehaviourUiState.Content
        assertEquals(1, content.notices.size)
        assertEquals(1L, content.notices.first().id)
        job.cancel()
    }

    @Test
    fun `summary counts by type`() = runTest(dispatcher) {
        val notices = listOf(
            notice(1, Notice.TYPE_POSITIVE, 1),
            notice(2, Notice.TYPE_POSITIVE, 1),
            notice(3, Notice.TYPE_NEGATIVE, 1),
            notice(4, Notice.TYPE_NEUTRAL, 1),
        )
        val vm = BehaviourViewModel(
            source = { flowOf(notices) },
            onMarkSeen = mockk(relaxed = true),
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val summary = (vm.uiState.value as BehaviourUiState.Content).summary
        assertEquals(2, summary.praises)
        assertEquals(1, summary.warnings)
        assertEquals(1, summary.other)
        job.cancel()
    }

    @Test
    fun `an unknown notice is listed but counted in no bucket`() = runTest(dispatcher) {
        val notices = listOf(
            notice(1, Notice.TYPE_POSITIVE, 1),
            notice(2, Notice.TYPE_NEGATIVE, 1),
            notice(3, Notice.TYPE_NEUTRAL, 1),
            notice(4, Notice.TYPE_UNKNOWN, 1),
        )
        val vm = BehaviourViewModel(
            source = { flowOf(notices) },
            onMarkSeen = mockk(relaxed = true),
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val content = vm.uiState.value as BehaviourUiState.Content
        assertEquals(4, content.notices.size)
        assertEquals(1, content.summary.praises)
        assertEquals(1, content.summary.warnings)
        assertEquals(1, content.summary.other)
        job.cancel()
    }

    @Test
    fun `an undated notice is kept under both semester filters`() = runTest(dispatcher) {
        val vm = BehaviourViewModel(
            source = { flowOf(mixedSemesters) },
            onMarkSeen = mockk(relaxed = true),
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val ids = { (vm.uiState.value as BehaviourUiState.Content).notices.map { it.id } }
        assertEquals(listOf(1L, 2L, 3L, 4L), ids())

        vm.setFilter(SemesterFilter.SEMESTER_1)
        advanceUntilIdle()
        assertEquals(listOf(1L, 3L, 4L), ids())

        vm.setFilter(SemesterFilter.SEMESTER_2)
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L, 4L), ids())
        job.cancel()
    }

    @Test
    fun `an undated notice is counted in both semesters' summaries`() = runTest(dispatcher) {
        val vm = BehaviourViewModel(
            source = { flowOf(mixedSemesters) },
            onMarkSeen = mockk(relaxed = true),
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        val summary = { (vm.uiState.value as BehaviourUiState.Content).summary }

        vm.setFilter(SemesterFilter.SEMESTER_1)
        advanceUntilIdle()
        assertEquals(BehaviourSummary(praises = 1, warnings = 1, other = 1), summary())

        vm.setFilter(SemesterFilter.SEMESTER_2)
        advanceUntilIdle()
        assertEquals(BehaviourSummary(praises = 0, warnings = 2, other = 1), summary())

        vm.setFilter(SemesterFilter.YEAR)
        advanceUntilIdle()
        assertEquals(BehaviourSummary(praises = 1, warnings = 2, other = 1), summary())
        job.cancel()
    }

    @Test
    fun `markSeen delegates once and is idempotent`() = runTest(dispatcher) {
        val seam: (NoticeFull) -> Unit = mockk(relaxed = true)
        val n = notice(1, Notice.TYPE_POSITIVE, 1, seen = false)
        val vm = BehaviourViewModel(
            source = { flowOf(listOf(n)) },
            onMarkSeen = seam,
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        vm.markSeen(n)
        vm.markSeen(n) // n.seen is now true -> no-op
        advanceUntilIdle()
        verify(exactly = 1) { seam(n) }
        job.cancel()
    }
}
