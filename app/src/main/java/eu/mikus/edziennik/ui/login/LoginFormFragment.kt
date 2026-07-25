/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ext.getEnum
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.QrScannerDialog

class LoginFormFragment : Fragment() {
    companion object {
        private const val TAG = "LoginFormFragment"
    }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private var vm: LoginViewModel? = null
    private val nav by lazy { activity.nav }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        vm = ViewModelProvider(requireActivity(), LoginViewModel.Factory(app))[LoginViewModel::class.java]

        val loginType = arguments?.getEnum<LoginType>("loginType") ?: return null
        val register = LoginInfo.list.firstOrNull { it.loginType == loginType } ?: return null
        val loginMode = arguments?.getEnum<LoginMode>("loginMode") ?: return null
        val mode = register.loginModes.firstOrNull { it.loginMode == loginMode } ?: return null

        // DEMO / empty-credentials modes short-circuit straight to Progress (as legacy onViewCreated did).
        if (mode.credentials.isEmpty()) {
            nav.navigate(R.id.loginProgressFragment, Bundle(
                "loginType" to loginType,
                "loginMode" to loginMode,
            ), activity.navOptions)
            return null
        }

        val fields = mode.credentials.filterIsInstance<LoginInfo.FormField>()
        val checkboxes = mode.credentials.filterIsInstance<LoginInfo.FormCheckbox>()

        val initialValues = fields.associate { it.keyName to (arguments?.getString(it.keyName) ?: "") }
        val initialChecks = checkboxes
            .filter { arguments?.containsKey(it.keyName) == true }
            .associate { it.keyName to (arguments?.getBoolean(it.keyName) ?: false) }

        // Map a carried-over lastError to a field error (first match) or the mode banner (§5.3).
        val (initialFieldErrors, initialBannerError) = mapLastError(mode)

        return ComposeView(inflater.context).apply {
            setAppThemeContent(forceLight = true) {
                LoginFormScreen(
                    register = register,
                    mode = mode,
                    initialValues = initialValues,
                    initialChecks = initialChecks,
                    initialFieldErrors = initialFieldErrors,
                    initialBannerError = initialBannerError,
                    onBack = { nav.navigateUp() },
                    onSubmit = { result -> onSubmit(loginType, loginMode, result) },
                    onQrScan = { field -> scanQrCode(field) },
                )
            }
        }
    }

    private fun mapLastError(mode: LoginInfo.Mode): Pair<Map<String, Int>, Int?> {
        val error = vm?.lastError ?: return emptyMap<String, Int>() to null
        vm?.clearError()
        for (credential in mode.credentials) {
            credential.errorCodes[error.errorCode]?.let { return mapOf(credential.keyName to it) to null }
        }
        mode.errorCodes[error.errorCode]?.let { return emptyMap<String, Int>() to it }
        return emptyMap<String, Int>() to null
    }

    private fun onSubmit(loginType: LoginType, loginMode: LoginMode, result: FormSubmit) {
        // Back-nav prefill: mirror validated values + checkboxes into arguments (matches legacy).
        result.fieldValues.forEach { (k, v) -> arguments?.putString(k, v) }
        result.checkboxValues.forEach { (k, v) -> arguments?.putBoolean(k, v) }

        if (result.hasErrors) return

        val payload = Bundle("loginType" to loginType, "loginMode" to loginMode)
        result.fieldValues.forEach { (k, v) -> payload.putString(k, v) }
        result.checkboxValues.forEach { (k, v) -> payload.putBoolean(k, v) }
        nav.navigate(R.id.loginProgressFragment, payload, activity.navOptions)
    }

    private fun scanQrCode(credential: LoginInfo.FormField) {
        val qrDecoderClass = credential.qrDecoderClass ?: return
        app.permissionManager.requestCameraPermission(activity, R.string.permissions_qr_scanner) {
            QrScannerDialog(activity, onCodeScanned = { code ->
                val decoder = qrDecoderClass.newInstance()
                val values = decoder.decode(code) ?: return@QrScannerDialog
                values.forEach { (keyName, fieldText) -> arguments?.putString(keyName, fieldText) }
                // Re-seed happens on recompose via arguments; the screen is re-created on nav return.
            }).show()
        }
    }
}
