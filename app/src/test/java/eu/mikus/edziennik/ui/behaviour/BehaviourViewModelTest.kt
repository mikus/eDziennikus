/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.behaviour

import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.data.db.full.NoticeFull
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

    private fun notice(id: Long, type: Int, semester: Int, seen: Boolean = false): NoticeFull =
        NoticeFull(
            profileId = 1, id = id, type = type, semester = semester,
            text = "t", category = null, points = null, teacherId = 0,
        ).also { it.seen = seen }

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
