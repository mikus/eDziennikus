/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-14.
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
import eu.mikus.edziennik.ui.compose.setAppThemeContent

class LoginSyncErrorFragment : Fragment() {
    companion object { private const val TAG = "LoginSyncErrorFragment" }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private lateinit var vm: LoginViewModel
    private val nav by lazy { activity.nav }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        vm = ViewModelProvider(requireActivity(), LoginViewModel.Factory(app))[LoginViewModel::class.java]
        val errorDetail = vm.lastError?.getStringReason(activity)
        vm.clearError()
        return ComposeView(inflater.context).apply {
            setAppThemeContent(forceLight = true) {
                LoginSyncErrorScreen(
                    errorDetail = errorDetail,
                    onNext = { nav.navigate(R.id.loginFinishFragment, null, activity.navOptions) },
                )
            }
        }
    }
}
