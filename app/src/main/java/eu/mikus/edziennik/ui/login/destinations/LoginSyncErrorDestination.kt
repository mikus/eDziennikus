/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login.destinations

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import eu.mikus.edziennik.ui.login.LoginRoute
import eu.mikus.edziennik.ui.login.LoginSyncErrorScreen
import eu.mikus.edziennik.ui.login.LoginViewModel

@Composable
fun LoginSyncErrorDestination(vm: LoginViewModel, navController: NavHostController, activity: AppCompatActivity) {
    val errorDetail = remember {
        val detail = vm.lastError?.getStringReason(activity)
        vm.clearError()
        detail
    }
    LoginSyncErrorScreen(
        errorDetail = errorDetail,
        onNext = { navController.navigate(LoginRoute.FINISH) },
    )
}
