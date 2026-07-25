/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.setAppThemeContent

class LoginSummaryFragment : Fragment() {
    companion object { private const val TAG = "LoginSummaryFragment" }

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
            setAppThemeContent(forceLight = true) {
                val profiles by vm.profiles.collectAsStateWithLifecycle()
                LoginSummaryScreen(
                    profiles = profiles,
                    onToggle = vm::toggleSelection,
                    onAddStudent = { nav.navigate(R.id.loginChooserFragment, null, activity.navOptions) },
                    onDone = { nav.navigate(R.id.loginSyncFragment, null, activity.navOptions) },
                )
            }
        }
    }
}
