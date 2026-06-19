/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.notifications

import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.data.db.enums.NotificationType
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
class NotificationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun notification(text: String): Notification =
        Notification(
            title = "t",
            text = text,
            type = NotificationType.GENERAL,
            profileId = 1,
            profileName = "p",
        )

    @Test
    fun `empty source yields Empty`() = runTest(dispatcher) {
        val vm = NotificationsViewModel(source = { flowOf(emptyList()) }, dispatcher = dispatcher)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(NotificationsUiState.Empty, vm.uiState.value)
        job.cancel()
    }

    @Test
    fun `non-empty source yields Content`() = runTest(dispatcher) {
        val vm = NotificationsViewModel(
            source = { flowOf(listOf(notification("a"), notification("b"))) },
            dispatcher = dispatcher,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is NotificationsUiState.Content)
        assertEquals(2, state.notifications.size)
        job.cancel()
    }
}
