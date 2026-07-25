/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login.destinations

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import eu.mikus.edziennik.ui.login.LoginRoute
import eu.mikus.edziennik.ui.login.LoginSyncScreen
import eu.mikus.edziennik.ui.login.LoginViewModel

@Composable
fun LoginSyncDestination(vm: LoginViewModel, navController: NavHostController) {
    val activity = LocalContext.current as AppCompatActivity
    val state by vm.syncState.collectAsStateWithLifecycle()

    // Collector registered BEFORE persistAndSync (matches legacy onViewCreated order; the P19b
    // phase-guard ensures no stale ToFinish is buffered here).
    LaunchedEffect(Unit) {
        vm.syncResult.collect { result ->
            when (result) {
                LoginViewModel.SyncResult.ToFinish -> navController.navigate(LoginRoute.FINISH)
                LoginViewModel.SyncResult.ToSyncError -> navController.navigate(LoginRoute.SYNC_ERROR)
            }
        }
    }
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) { started = true; vm.persistAndSync(activity) }
    }

    LoginSyncScreen(state)
}
