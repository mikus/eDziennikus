/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private fun snap(snowfall: Boolean = false, syncEnabled: Boolean = true) = SettingsSnapshot(
        profileName = "Jan", profileSubname = null,
        snowfallWindow = true, snowfall = snowfall, eggfallNear = false, eggfall = false,
        themeName = "T", miniDrawer = false, openDrawerOnBack = false,
        syncEnabled = syncEnabled, syncInterval = "1h", onlyWifi = false,
        quietHoursEnabled = false, quietHours = "q", notifyUpdates = true, sdkAtLeastKitKat = true,
        hasTimetable = false, hasAgenda = false, hasGrades = false, hasMessages = false, hasAttendance = false,
        bellSync = "off", countInSeconds = false, isLibrus = false,
        showTeacherAbsences = true, devMode = false, hideSticksFromOld = false,
        versionText = "4.0, debug",
    )

    @Test
    fun `initial state is built from the snapshot`() {
        val vm = SettingsViewModel(buildSnapshot = { snap() }, writeToggle = { _, _ -> })
        assertEquals(5, vm.uiState.value.cards.size)
    }

    @Test
    fun `onToggle writes through the seam then refreshes with the new snapshot`() {
        var current = snap(snowfall = false)
        val writes = mutableListOf<Pair<SettingsToggle, Boolean>>()
        val vm = SettingsViewModel(
            buildSnapshot = { current },
            writeToggle = { t, v -> writes.add(t to v); if (t == SettingsToggle.SNOWFALL) current = snap(snowfall = v) },
        )
        vm.onToggle(SettingsToggle.SNOWFALL, true)
        assertEquals(listOf(SettingsToggle.SNOWFALL to true), writes)
        val snowflake = vm.uiState.value.cards[1].items
            .filterIsInstance<SettingsItem.Switch>().single { it.toggle == SettingsToggle.SNOWFALL }
        assertTrue(snowflake.checked)   // rebuilt from the refreshed snapshot
    }

    @Test
    fun `snowfall toggle emits Recreate`() = runTest {
        val vm = SettingsViewModel(buildSnapshot = { snap() }, writeToggle = { _, _ -> })
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects.add(it) } }
        vm.onToggle(SettingsToggle.SNOWFALL, true)
        assertEquals(listOf(SettingsEffect.Recreate), effects)
    }

    @Test
    fun `sync toggles emit RescheduleSync and updates emit RescheduleUpdate`() = runTest {
        val vm = SettingsViewModel(buildSnapshot = { snap() }, writeToggle = { _, _ -> })
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects.add(it) } }
        vm.onToggle(SettingsToggle.SYNC_ENABLED, false)
        vm.onToggle(SettingsToggle.SYNC_ONLY_WIFI, true)
        vm.onToggle(SettingsToggle.NOTIFY_UPDATES, false)
        vm.onToggle(SettingsToggle.MINI_DRAWER, true)
        assertEquals(
            listOf(SettingsEffect.RescheduleSync, SettingsEffect.RescheduleSync, SettingsEffect.RescheduleUpdate, SettingsEffect.RefreshDrawer),
            effects,
        )
    }

    @Test
    fun `count-in-seconds toggle emits no effect`() = runTest {
        val vm = SettingsViewModel(buildSnapshot = { snap() }, writeToggle = { _, _ -> })
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects.add(it) } }
        vm.onToggle(SettingsToggle.COUNT_IN_SECONDS, true)
        assertTrue(effects.isEmpty())
    }
}
