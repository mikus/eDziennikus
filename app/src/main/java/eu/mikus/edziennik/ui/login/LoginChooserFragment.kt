/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.utils.BetterLinkMovementMethod
import eu.mikus.edziennik.utils.Utils
import eu.mikus.edziennik.utils.html.BetterHtml
import eu.mikus.edziennik.utils.models.Date

class LoginChooserFragment : Fragment() {
    companion object {
        private const val TAG = "LoginChooserFragment"
    }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private val nav by lazy { activity.nav }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App

        if (!app.permissionManager.isNotificationPermissionGranted) {
            app.permissionManager.requestNotificationsPermission(activity, 0, false) {}
        }

        val versionText = getString(
            R.string.login_chooser_version_format,
            app.buildManager.versionName,
            Date.fromMillis(app.buildManager.buildTimestamp).stringY_m_d,
        )

        val cancelVisible = activity.loginStores.isNotEmpty() || app.config.loginFinished

        return ComposeView(inflater.context).apply {
            setAppThemeContent(forceLight = true) {
                LoginChooserScreen(
                    versionText = versionText,
                    cancelVisible = cancelVisible,
                    onModeClick = ::onLoginModeClicked,
                    onVersionClick = { app.buildManager.showVersionDialog(activity) },
                    onHelpClick = { Utils.openUrl(activity, "https://github.com/mikus/eDziennikus/issues") },
                    onCancel = ::onCancel,
                )
            }
        }
    }

    private fun onCancel() {
        when {
            activity.loginStores.isNotEmpty() -> nav.navigateUp()
            app.config.loginFinished -> {
                activity.setResult(Activity.RESULT_CANCELED)
                activity.finish()
            }
        }
    }

    private fun onLoginModeClicked(loginType: LoginInfo.Register, loginMode: LoginInfo.Mode) {
        if (!app.config.privacyPolicyAccepted) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.privacy_policy)
                .setMessage(BetterHtml.fromHtml(activity, R.string.privacy_policy_dialog_html))
                .setPositiveButton(R.string.i_agree) { _, _ ->
                    app.config.privacyPolicyAccepted = true
                    onLoginModeClicked(loginType, loginMode)
                }
                .setNegativeButton(R.string.i_disagree, null)
                .show()
                .also { dialog ->
                    dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                        BetterLinkMovementMethod.getInstance()
                }
            return
        }

        if (loginMode.isTesting || loginMode.isDevOnly) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.login_chooser_testing_title)
                .setMessage(R.string.login_chooser_testing_text)
                .setPositiveButton(R.string.ok) { _, _ -> navigateToLoginMode(loginType, loginMode) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        navigateToLoginMode(loginType, loginMode)
    }

    private fun navigateToLoginMode(loginType: LoginInfo.Register, loginMode: LoginInfo.Mode) {
        nav.navigate(R.id.loginFormFragment, eu.mikus.edziennik.ext.Bundle(
            "loginType" to loginType.loginType,
            "loginMode" to loginMode.loginMode,
        ), activity.navOptions)
    }
}
