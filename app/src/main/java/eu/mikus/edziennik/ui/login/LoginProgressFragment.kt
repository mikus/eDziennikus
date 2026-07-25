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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.LOGIN_NO_ARGUMENTS
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.utils.managers.UserActionManager
import kotlinx.coroutines.launch

class LoginProgressFragment : Fragment() {
    companion object { private const val TAG = "LoginProgressFragment" }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private lateinit var vm: LoginViewModel
    private val nav by lazy { activity.nav }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        vm = ViewModelProvider(requireActivity(), LoginViewModel.Factory(app))[LoginViewModel::class.java]
        return ComposeView(inflater.context).apply {
            setAppThemeContent(forceLight = true) { LoginProgressScreen() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.loginResult.collect { result ->
                        when (result) {
                            LoginViewModel.LoginResult.ToSummary -> {
                                activity.errorSnackbar.dismiss()
                                nav.navigate(R.id.loginSummaryFragment, null, activity.navOptions)
                            }
                            LoginViewModel.LoginResult.NoStudents -> showNoStudents()
                            LoginViewModel.LoginResult.Error -> nav.navigateUp()
                        }
                    }
                }
                launch {
                    vm.userActionEvents.collect { event -> runUserAction(event) }
                }
            }
        }

        val args = arguments ?: run {
            vm.reportError(ApiError(TAG, LOGIN_NO_ARGUMENTS))
            nav.navigateUp()
            return
        }
        activity.errorSnackbar.dismiss()
        if (savedInstanceState == null) vm.startFirstLogin(activity, args)
    }

    private fun showNoStudents() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.login_account_no_students)
            .setMessage(R.string.login_account_no_students_text)
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener { nav.navigateUp() }
            .show()
    }

    private fun runUserAction(event: UserActionRequiredEvent) {
        val args = arguments ?: run {
            vm.reportError(ApiError(TAG, LOGIN_NO_ARGUMENTS)); nav.navigateUp(); return
        }
        val callback = UserActionManager.UserActionCallback(
            onSuccess = { data -> args.putAll(data); vm.startFirstLogin(activity, args) },
            onFailure = { vm.reportError(ApiError(TAG, eu.mikus.edziennik.data.api.ERROR_REQUIRES_USER_ACTION)); nav.navigateUp() },
            onCancel = { nav.navigateUp() },
        )
        app.userActionManager.execute(activity, event, callback)
    }
}
