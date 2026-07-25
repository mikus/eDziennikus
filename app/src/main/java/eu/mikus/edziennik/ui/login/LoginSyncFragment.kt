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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import kotlinx.coroutines.launch

class LoginSyncFragment : Fragment() {
    companion object { private const val TAG = "LoginSyncFragment" }

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
                val state by vm.syncState.collectAsStateWithLifecycle()
                LoginSyncScreen(state)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.syncResult.collect { result ->
                    when (result) {
                        LoginViewModel.SyncResult.ToFinish -> nav.navigate(R.id.loginFinishFragment, null, activity.navOptions)
                        LoginViewModel.SyncResult.ToSyncError -> nav.navigate(R.id.loginSyncErrorFragment, null, activity.navOptions)
                    }
                }
            }
        }
        if (savedInstanceState == null) vm.persistAndSync(activity)
    }
}
