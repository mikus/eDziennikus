/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login.destinations

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ui.login.LoginChooserScreen
import eu.mikus.edziennik.ui.login.LoginInfo
import eu.mikus.edziennik.ui.login.LoginRoute
import eu.mikus.edziennik.ui.login.LoginViewModel
import eu.mikus.edziennik.ui.login.executeLoginBack
import eu.mikus.edziennik.ui.login.loginBackPolicy
import eu.mikus.edziennik.utils.BetterLinkMovementMethod
import eu.mikus.edziennik.utils.Utils
import eu.mikus.edziennik.utils.html.BetterHtml
import eu.mikus.edziennik.utils.models.Date

@Composable
fun LoginChooserDestination(vm: LoginViewModel, navController: NavHostController, activity: AppCompatActivity) {
    val app = activity.application as App

    LaunchedEffect(Unit) {
        if (!app.permissionManager.isNotificationPermissionGranted) {
            app.permissionManager.requestNotificationsPermission(activity, 0, false) {}
        }
    }

    val versionText = stringResource(
        R.string.login_chooser_version_format,
        app.buildManager.versionName,
        Date.fromMillis(app.buildManager.buildTimestamp).stringY_m_d,
    )
    val cancelVisible = vm.hasLoginStores || app.config.loginFinished

    LoginChooserScreen(
        versionText = versionText,
        cancelVisible = cancelVisible,
        onModeClick = { register, mode -> onLoginModeClicked(activity, app, vm, navController, register, mode) },
        onVersionClick = { app.buildManager.showVersionDialog(activity) },
        onHelpClick = { Utils.openUrl(activity, "https://github.com/mikus/eDziennikus/issues") },
        onCancel = { executeLoginBack(loginBackPolicy(LoginRoute.CHOOSER, vm.hasLoginStores), navController, activity) },
    )
}

private fun onLoginModeClicked(
    activity: AppCompatActivity, app: App, vm: LoginViewModel, navController: NavHostController,
    register: LoginInfo.Register, mode: LoginInfo.Mode,
) {
    if (!app.config.privacyPolicyAccepted) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.privacy_policy)
            .setMessage(BetterHtml.fromHtml(activity, R.string.privacy_policy_dialog_html))
            .setPositiveButton(R.string.i_agree) { _, _ ->
                app.config.privacyPolicyAccepted = true
                onLoginModeClicked(activity, app, vm, navController, register, mode)
            }
            .setNegativeButton(R.string.i_disagree, null)
            .show()
            .also { dialog ->
                dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                    BetterLinkMovementMethod.getInstance()
            }
        return
    }
    if (mode.isTesting || mode.isDevOnly) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.login_chooser_testing_title)
            .setMessage(R.string.login_chooser_testing_text)
            .setPositiveButton(R.string.ok) { _, _ -> navigateToLoginMode(vm, navController, register, mode) }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return
    }
    navigateToLoginMode(vm, navController, register, mode)
}

private fun navigateToLoginMode(
    vm: LoginViewModel, navController: NavHostController,
    register: LoginInfo.Register, mode: LoginInfo.Mode,
) {
    if (mode.credentials.isEmpty()) {
        // DEMO / empty-credentials modes skip the Form entirely (spec §8 — removes the legacy self-bounce
        // loop); their args ride the VM straight to Progress.
        vm.stageLoginArgs(Bundle("loginType" to register.loginType, "loginMode" to mode.loginMode))
        navController.navigate(LoginRoute.PROGRESS)
    } else {
        // Credentialed modes carry loginType/loginMode as route args so they survive process death
        // (nav-compose persists the back stack + args; the VM's loginArgs does not).
        navController.navigate("${LoginRoute.FORM}/${register.loginType.name}/${mode.loginMode.name}")
    }
}
