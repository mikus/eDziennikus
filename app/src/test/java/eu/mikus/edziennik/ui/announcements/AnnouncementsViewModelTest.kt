/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.announcements

import eu.mikus.edziennik.data.db.full.AnnouncementFull
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

/**
 * NOTE: The Compose UI test for `AnnouncementsScreen` (Task 5) was deferred — `createComposeRule`
 * fails to host under Robolectric here (`RuntimeException at RoboMonitoringInstrumentation`), a
 * known Robolectric+Compose instrumentation limitation. Per the plan's pre-authorized fallback,
 * the screen's behavior is covered by these ViewModel mapping tests plus the Phase 0 manual matrix;
 * `androidTest` is intentionally not wired up. Revisit when Robolectric/Compose interop improves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `empty source yields Empty`() = runTest(dispatcher) {
        val vm = AnnouncementsViewModel(
            source = { flowOf(emptyList()) },
            profileId = 1,
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(AnnouncementsUiState.Empty, vm.uiState.value)
        job.cancel()
    }

    @Test
    fun `non-empty source yields Content and applies filterNotes per item`() = runTest(dispatcher) {
        val item = mockk<AnnouncementFull>(relaxed = true)
        val vm = AnnouncementsViewModel(
            source = { flowOf(listOf(item)) },
            profileId = 1,
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is AnnouncementsUiState.Content)
        assertEquals(1, state.announcements.size)
        verify { item.filterNotes() }
        job.cancel()
    }
}
