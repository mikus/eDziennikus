/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.ext.Intent
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.compose.setAppThemeContent

class LoginFinishFragment : Fragment() {
    companion object { private const val TAG = "LoginFinishFragment" }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private lateinit var vm: LoginViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        vm = ViewModelProvider(requireActivity(), LoginViewModel.Factory(app))[LoginViewModel::class.java]

        val firstRun = !app.config.loginFinished
        app.config.loginFinished = true

        return ComposeView(inflater.context).apply {
            setAppThemeContent(forceLight = true) {
                LoginFinishScreen(firstRun = firstRun, onDone = { finish(firstRun) })
            }
        }
    }

    private fun finish(firstRun: Boolean) {
        val firstProfileId = vm.firstProfileId
        if (firstProfileId == 0) { activity.finish(); return }
        app.profileLoad(firstProfileId) {
            if (firstRun) {
                activity.startActivity(Intent(activity, MainActivity::class.java, "profileId" to firstProfileId, "fragmentId" to NavTarget.HOME))
            } else {
                activity.setResult(Activity.RESULT_OK, Intent(null, "profileId" to firstProfileId, "fragmentId" to NavTarget.HOME))
            }
            activity.finish()
        }
    }
}
