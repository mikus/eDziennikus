/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.error.ErrorDetailsDialog
import eu.mikus.edziennik.ui.login.destinations.LoginChooserDestination
import eu.mikus.edziennik.ui.login.destinations.LoginFinishDestination
import eu.mikus.edziennik.ui.login.destinations.LoginFormDestination
import eu.mikus.edziennik.ui.login.destinations.LoginProgressDestination
import eu.mikus.edziennik.ui.login.destinations.LoginSummaryDestination
import eu.mikus.edziennik.ui.login.destinations.LoginSyncDestination
import eu.mikus.edziennik.ui.login.destinations.LoginSyncErrorDestination

@Composable
fun LoginRoot(vm: LoginViewModel) {
    val activity = LocalContext.current as AppCompatActivity   // = LoginActivity
    val navController = rememberNavController()
    // loginBackPolicy is the SOLE back authority — disable nav-compose's own OnBackPressedCallback.
    navController.enableOnBackPressed(false)
    val snackbarHostState = remember { SnackbarHostState() }

    // Error surface (replaces the View-based ErrorSnackbar for the login flow).
    LaunchedEffect(Unit) {
        vm.errorEvents.collect { error ->
            val result = snackbarHostState.showSnackbar(
                message = error.getStringReason(activity),
                actionLabel = activity.getString(R.string.more),
            )
            if (result == SnackbarResult.ActionPerformed) {
                ErrorDetailsDialog(activity, listOf(error)).show()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = LoginRoute.CHOOSER,
            // .imePadding() consumes the keyboard inset so the credentialed Form's fields stay
            // visible — legacy relied on adjustResize below API 35, which enableEdgeToEdge disables.
            modifier = Modifier.padding(padding).imePadding(),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
        ) {
            composable(LoginRoute.CHOOSER) { LoginChooserDestination(vm, navController, activity) }
            composable(
                route = LoginRoute.FORM + "/{loginType}/{loginMode}",
                arguments = listOf(
                    navArgument("loginType") { type = NavType.StringType },
                    navArgument("loginMode") { type = NavType.StringType },
                ),
            ) { entry -> LoginFormDestination(vm, navController, activity, entry) }
            composable(LoginRoute.PROGRESS) { LoginProgressDestination(vm, navController, snackbarHostState) }
            composable(LoginRoute.SUMMARY) { LoginSummaryDestination(vm, navController) }
            composable(LoginRoute.SYNC) { LoginSyncDestination(vm, navController) }
            composable(LoginRoute.SYNC_ERROR) { LoginSyncErrorDestination(vm, navController, activity) }
            composable(LoginRoute.FINISH) { LoginFinishDestination(vm, activity) }
        }
        // Composed AFTER NavHost so this callback wins dispatch (belt-and-suspenders with enableOnBackPressed(false)).
        LoginBackHandler(navController, vm, activity)
    }
}

@Composable
private fun LoginBackHandler(navController: NavHostController, vm: LoginViewModel, activity: AppCompatActivity) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    BackHandler(enabled = true) {
        executeLoginBack(loginBackPolicy(route, vm.hasLoginStores), navController, activity)
    }
}

/** Shared effect executor for both the BackHandler and the Chooser Cancel button. */
internal fun executeLoginBack(action: LoginBackAction, navController: NavHostController, activity: AppCompatActivity) {
    when (action) {
        LoginBackAction.Consume -> { /* swallow */ }
        LoginBackAction.ConfirmCancel -> MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.are_you_sure)
            .setMessage(R.string.login_cancel_confirmation)
            .setPositiveButton(R.string.yes) { _, _ -> cancelLogin(activity) }
            .setNegativeButton(R.string.no, null)
            .show()
        LoginBackAction.CancelToHost -> cancelLogin(activity)
        LoginBackAction.Up -> navController.popBackStack()
    }
}

private fun cancelLogin(activity: AppCompatActivity) {
    activity.setResult(Activity.RESULT_CANCELED)
    activity.finish()
}
