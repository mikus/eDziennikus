/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.data

import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskStartedEvent
import eu.mikus.edziennik.data.api.models.ApiError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.greenrobot.eventbus.EventBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncStatusTest {
    private val status = SyncStatus()

    @AfterEach fun tearDown() {
        EventBus.getDefault().unregister(status)
        unmockkObject(SyncStatus.Companion)
    }

    @Test fun `starts not refreshing`() {
        assertFalse(status.isRefreshing.value)
    }

    @Test fun `onStarted for current profile sets refreshing`() {
        // Mock the profileId seam so the test never touches App.profile (lateinit, unset in plain JVM).
        mockkObject(SyncStatus.Companion)
        every { SyncStatus.currentProfileId() } returns 1
        status.onStarted(ApiTaskStartedEvent(profileId = 1))
        assertTrue(status.isRefreshing.value)
    }

    @Test fun `onStarted for other profile leaves refreshing unchanged`() {
        mockkObject(SyncStatus.Companion)
        every { SyncStatus.currentProfileId() } returns 1
        status.onStarted(ApiTaskStartedEvent(profileId = 999))
        assertFalse(status.isRefreshing.value)
    }

    @Test fun `markRefreshing sets refreshing`() {
        status.markRefreshing()
        assertTrue(status.isRefreshing.value)
    }

    @Test fun `onAllFinished clears refreshing`() {
        status.markRefreshing()
        status.onAllFinished(ApiTaskAllFinishedEvent())
        assertFalse(status.isRefreshing.value)
    }

    @Test fun `onError clears refreshing`() {
        status.markRefreshing()
        status.onError(ApiTaskErrorEvent(mockk<ApiError>()))
        assertFalse(status.isRefreshing.value)
    }
}
