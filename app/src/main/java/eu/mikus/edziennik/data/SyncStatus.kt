/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.data

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskStartedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * App-scoped, single EventBus subscriber for "a sync is in progress", exposing an observable the
 * Compose feature screens read to drive their PullToRefreshBox. Profile-scoped to the current
 * profile (only the visible profile's sync spins). Registered for the app's lifetime; never
 * unregistered (App is a process singleton). Deliberately does NOT removeStickyEvent — MainActivity
 * owns the sticky lifecycle for AllFinished/Error; this class only reads.
 */
class SyncStatus {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init { EventBus.getDefault().register(this) }

    /** Optimistic eager-true from [eu.mikus.edziennik.ui.base.syncFeature], so the pull indicator
     *  stays continuous from gesture-release until the async ApiTaskStartedEvent arrives. */
    fun markRefreshing() { _isRefreshing.value = true }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStarted(event: ApiTaskStartedEvent) {
        if (event.profileId == currentProfileId()) _isRefreshing.value = true
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onAllFinished(event: ApiTaskAllFinishedEvent) { _isRefreshing.value = false }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onError(event: ApiTaskErrorEvent) { _isRefreshing.value = false }

    companion object {
        /** Seam over App.profileId so onStarted stays unit-testable without booting App. */
        fun currentProfileId(): Int = App.profileId
    }
}
