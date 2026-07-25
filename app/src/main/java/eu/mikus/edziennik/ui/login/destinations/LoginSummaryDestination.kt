/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import eu.mikus.edziennik.ui.login.LoginRoute
import eu.mikus.edziennik.ui.login.LoginSummaryScreen
import eu.mikus.edziennik.ui.login.LoginViewModel

@Composable
fun LoginSummaryDestination(vm: LoginViewModel, navController: NavHostController) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    LoginSummaryScreen(
        profiles = profiles,
        onToggle = vm::toggleSelection,
        onAddStudent = { navController.navigate(LoginRoute.CHOOSER) },
        onDone = { navController.navigate(LoginRoute.SYNC) },
    )
}
