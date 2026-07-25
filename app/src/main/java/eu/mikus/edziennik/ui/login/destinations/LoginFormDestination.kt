/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login.destinations

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ui.dialogs.QrScannerDialog
import eu.mikus.edziennik.ui.login.FormSubmit
import eu.mikus.edziennik.ui.login.LoginFormScreen
import eu.mikus.edziennik.ui.login.LoginInfo
import eu.mikus.edziennik.ui.login.LoginRoute
import eu.mikus.edziennik.ui.login.LoginViewModel

@Composable
fun LoginFormDestination(
    vm: LoginViewModel, navController: NavHostController, activity: AppCompatActivity, entry: NavBackStackEntry,
) {
    val app = activity.application as App
    // loginType/loginMode ride the nav route (persisted across process death), NOT the transient
    // vm.loginArgs — so the Form re-renders intact after a low-memory kill.
    val loginType = entry.arguments?.getString("loginType")
        ?.let { runCatching { LoginType.valueOf(it) }.getOrNull() } ?: return
    val register = LoginInfo.list.firstOrNull { it.loginType == loginType } ?: return
    val loginMode = entry.arguments?.getString("loginMode")
        ?.let { runCatching { LoginMode.valueOf(it) }.getOrNull() } ?: return
    val mode = register.loginModes.firstOrNull { it.loginMode == loginMode } ?: return

    // One-shot read+clear of vm.lastError, once per Form entry (spec §10 risk 1).
    val errorSeed = remember { mapLastError(vm, mode) }

    LoginFormScreen(
        register = register,
        mode = mode,
        // Fresh seeds only — the field values persist via LoginFormScreen's own rememberSaveable
        // (back-stack retention) and the QR apply-callback, so no back-prefill from args is needed.
        initialValues = emptyMap(),
        initialChecks = emptyMap(),
        initialFieldErrors = errorSeed.first,
        initialBannerError = errorSeed.second,
        onBack = { navController.popBackStack() },
        onSubmit = { result -> onFormSubmit(vm, navController, loginType, loginMode, result) },
        onQrScan = { field, apply -> scanQrCode(activity, app, field, apply) },
    )
}

private fun mapLastError(vm: LoginViewModel, mode: LoginInfo.Mode): Pair<Map<String, Int>, Int?> {
    val error = vm.lastError ?: return emptyMap<String, Int>() to null
    vm.clearError()
    for (credential in mode.credentials) {
        credential.errorCodes[error.errorCode]?.let { return mapOf(credential.keyName to it) to null }
    }
    mode.errorCodes[error.errorCode]?.let { return emptyMap<String, Int>() to it }
    return emptyMap<String, Int>() to null
}

private fun onFormSubmit(
    vm: LoginViewModel, navController: NavHostController,
    loginType: LoginType, loginMode: LoginMode, result: FormSubmit,
) {
    if (result.hasErrors) return
    val payload = Bundle("loginType" to loginType, "loginMode" to loginMode)
    result.fieldValues.forEach { (k, v) -> payload.putString(k, v) }
    result.checkboxValues.forEach { (k, v) -> payload.putBoolean(k, v) }
    vm.stageLoginArgs(payload)
    navController.navigate(LoginRoute.PROGRESS)
}

private fun scanQrCode(
    activity: AppCompatActivity, app: App,
    credential: LoginInfo.FormField, apply: (Map<String, String>) -> Unit,
) {
    val qrDecoderClass = credential.qrDecoderClass ?: return
    app.permissionManager.requestCameraPermission(activity, R.string.permissions_qr_scanner) {
        QrScannerDialog(activity, onCodeScanned = { code ->
            val decoder = qrDecoderClass.newInstance()
            val values = decoder.decode(code) ?: return@QrScannerDialog
            apply(values)
        }).show()
    }
}
