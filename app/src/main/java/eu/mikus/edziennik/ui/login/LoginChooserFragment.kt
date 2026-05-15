/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.databinding.LoginChooserFragmentBinding
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.utils.BetterLinkMovementMethod
import eu.mikus.edziennik.utils.Utils
import eu.mikus.edziennik.utils.SimpleDividerItemDecoration
import eu.mikus.edziennik.utils.html.BetterHtml
import eu.mikus.edziennik.utils.models.Date
import kotlin.coroutines.CoroutineContext

class LoginChooserFragment : Fragment(), CoroutineScope {
    companion object {
        private const val TAG = "LoginChooserFragment"
    }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private lateinit var b: LoginChooserFragmentBinding
    private val nav by lazy { activity.nav }

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val manager
        get() = app.permissionManager
    // local/private variables go here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = LoginChooserFragmentBinding.inflate(inflater)
        return b.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!isAdded) return

        val adapter = LoginChooserAdapter(activity, this::onLoginModeClicked)
        if (!manager.isNotificationPermissionGranted) {
            manager.requestNotificationsPermission(activity, 0, false){}
        }

        b.versionText.setText(
            R.string.login_chooser_version_format,
            app.buildManager.versionName,
            Date.fromMillis(app.buildManager.buildTimestamp).stringY_m_d
        )
        b.versionText.onClick {
            app.buildManager.showVersionDialog(activity)
        }

        LoginInfo.chooserList = LoginInfo.chooserList
                ?: LoginInfo.list.toMutableList()

        adapter.items = LoginInfo.chooserList!!
        b.list.adapter = adapter
        b.list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(SimpleDividerItemDecoration(context))
        }

        b.helpButton.onClick {
            Utils.openUrl(activity, "https://github.com/mikus/eDziennikus/issues")
        }

        when {
            activity.loginStores.isNotEmpty() -> {
                // we are navigated here from LoginSummary
                b.cancelButton.isVisible = true
                b.cancelButton.onClick { nav.navigateUp() }
            }
            app.config.loginFinished -> {
                // we are navigated here from AppDrawer
                b.cancelButton.isVisible = true
                b.cancelButton.onClick {
                    activity.setResult(Activity.RESULT_CANCELED)
                    activity.finish()
                }
            }
            else -> {
                // there are no profiles
                b.cancelButton.isVisible = false
            }
        }
    }

    private fun onLoginModeClicked(
            loginType: LoginInfo.Register,
            loginMode: LoginInfo.Mode
    ) {
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

        launch {
            if (loginMode.isTesting || loginMode.isDevOnly) {
                MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.login_chooser_testing_title)
                        .setMessage(R.string.login_chooser_testing_text)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            navigateToLoginMode(loginType, loginMode)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                return@launch
            }

            navigateToLoginMode(loginType, loginMode)
        }
    }

    private fun navigateToLoginMode(loginType: LoginInfo.Register, loginMode: LoginInfo.Mode) {
        nav.navigate(R.id.loginFormFragment, Bundle(
                "loginType" to loginType.loginType,
                "loginMode" to loginMode.loginMode
        ), activity.navOptions)
    }

}
