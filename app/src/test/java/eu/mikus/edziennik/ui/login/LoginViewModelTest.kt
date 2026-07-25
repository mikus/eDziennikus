/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskProgressEvent
import eu.mikus.edziennik.data.api.events.FirstLoginFinishedEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoginViewModelTest {

    // startFirstLogin/persistAndSync launch on viewModelScope (Dispatchers.Main.immediate);
    // provide a Main dispatcher so those phase transitions are exercisable off-device.
    @BeforeEach fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun vm() = LoginViewModel(
        dbLastProfileId = { 0 },
        persist = { _, _ -> },
        enqueueFirstLogin = { _, _ -> },
        enqueueSync = { _, _ -> },
        resolveModeIcon = { 0 },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun profile(id: Int, storeId: Int): Profile = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { loginStoreId } returns storeId
        every { studentClassName } returns "1A"
        every { studentSchoolYearStart } returns 2025
        every { name } returns "Jan"
    }

    private fun store(id: Int): LoginStore = mockk(relaxed = true) { every { this@mockk.id } returns id }

    @Test fun `first login finished with profiles populates and routes to summary`() = runTest {
        val vm = vm()
        vm.onFirstLoginFinished(FirstLoginFinishedEvent(listOf(profile(1, 10)), store(10)))
        assertEquals(1, vm.profiles.value.size)
        assertTrue(vm.profiles.value.first().isSelected)
        assertTrue(vm.hasLoginStores)
        assertEquals(LoginViewModel.LoginResult.ToSummary, vm.loginResult.first())
    }

    @Test fun `empty profile list routes to no students`() = runTest {
        val vm = vm()
        vm.onFirstLoginFinished(FirstLoginFinishedEvent(emptyList(), store(10)))
        assertEquals(0, vm.profiles.value.size)
        assertEquals(LoginViewModel.LoginResult.NoStudents, vm.loginResult.first())
    }

    @Test fun `toggle selection flips isSelected`() = runTest {
        val vm = vm()
        vm.onFirstLoginFinished(FirstLoginFinishedEvent(listOf(profile(1, 10)), store(10)))
        vm.toggleSelection(1)
        assertTrue(vm.profiles.value.none { it.isSelected })
    }

    @Test fun `progress event maps to sync state, non-positive is indeterminate`() = runTest {
        val vm = vm()
        vm.onApiTaskProgress(ApiTaskProgressEvent(1, 42f, "syncing"))
        assertEquals(42f, vm.syncState.value.progress)
        assertEquals("syncing", vm.syncState.value.progressText)
        vm.onApiTaskProgress(ApiTaskProgressEvent(1, 0f, "x"))
        assertNull(vm.syncState.value.progress)
    }

    @Test fun `all finished during sync phase routes to finish`() = runTest {
        val vm = vm()
        vm.persistAndSync(mockk(relaxed = true)) // enter Sync phase
        vm.onApiTaskAllFinished(ApiTaskAllFinishedEvent())
        assertEquals(LoginViewModel.SyncResult.ToFinish, vm.syncResult.first())
    }

    @Test fun `all finished during progress phase does not leak a stale finish`() = runTest {
        val vm = vm()
        // The firstLogin task itself ends with an ApiTaskAllFinishedEvent while still in the
        // Progress phase; it must NOT buffer a ToFinish that would later skip the Sync screen.
        vm.onApiTaskAllFinished(ApiTaskAllFinishedEvent())
        vm.persistAndSync(mockk(relaxed = true)) // enter Sync phase
        vm.onApiTaskAllFinished(ApiTaskAllFinishedEvent())
        // Exactly one ToFinish is delivered (the Sync-phase one); the Progress-phase one was dropped.
        assertEquals(LoginViewModel.SyncResult.ToFinish, vm.syncResult.first())
        assertNull(withTimeoutOrNull(50) { vm.syncResult.first() })
    }

    @Test fun `error sets lastError and emits error event`() = runTest {
        val vm = vm()
        val err = ApiError("T", 123)
        vm.reportError(err)
        assertEquals(err, vm.lastError)
        assertEquals(err, vm.errorEvents.first())
    }

    @Test fun `stageLoginArgs stores the args for the progress destination to read`() {
        val vm = vm()
        val bundle = mockk<android.os.Bundle>(relaxed = true)
        vm.stageLoginArgs(bundle)
        assertEquals(bundle, vm.loginArgs)
    }
}
