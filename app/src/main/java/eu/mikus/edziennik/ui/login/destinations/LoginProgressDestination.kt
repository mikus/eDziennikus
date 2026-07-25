/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login.destinations

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.ERROR_REQUIRES_USER_ACTION
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.ui.login.LoginProgressScreen
import eu.mikus.edziennik.ui.login.LoginRoute
import eu.mikus.edziennik.ui.login.LoginViewModel
import eu.mikus.edziennik.utils.managers.UserActionManager

private const val TAG = "LoginProgressDestination"

@Composable
fun LoginProgressDestination(vm: LoginViewModel, navController: NavHostController, snackbarHostState: SnackbarHostState) {
    val activity = LocalContext.current as AppCompatActivity
    val app = activity.application as App

    LaunchedEffect(Unit) {
        launch {
            vm.loginResult.collect { result ->
                when (result) {
                    LoginViewModel.LoginResult.ToSummary -> {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        navController.navigate(LoginRoute.SUMMARY)
                    }
                    LoginViewModel.LoginResult.NoStudents -> showNoStudents(activity, navController)
                    LoginViewModel.LoginResult.Error -> navController.popBackStack()
                }
            }
        }
        launch {
            vm.userActionEvents.collect { event -> runUserAction(activity, app, vm, navController, event) }
        }
    }

    // Fire the first-login task exactly once per entry (replaces savedInstanceState == null).
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            snackbarHostState.currentSnackbarData?.dismiss()
            vm.startFirstLogin(activity, vm.loginArgs)
        }
    }

    LoginProgressScreen()
}

private fun showNoStudents(activity: AppCompatActivity, navController: NavHostController) {
    MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.login_account_no_students)
        .setMessage(R.string.login_account_no_students_text)
        .setPositiveButton(R.string.ok, null)
        .setOnDismissListener { navController.popBackStack() }
        .show()
}

private fun runUserAction(
    activity: AppCompatActivity, app: App, vm: LoginViewModel,
    navController: NavHostController, event: UserActionRequiredEvent,
) {
    val args = vm.loginArgs
    val callback = UserActionManager.UserActionCallback(
        onSuccess = { data -> args.putAll(data); vm.stageLoginArgs(args); vm.startFirstLogin(activity, args) },
        onFailure = { vm.reportError(ApiError(TAG, ERROR_REQUIRES_USER_ACTION)); navController.popBackStack() },
        onCancel = { navController.popBackStack() },
    )
    app.userActionManager.execute(activity, event, callback)
}
