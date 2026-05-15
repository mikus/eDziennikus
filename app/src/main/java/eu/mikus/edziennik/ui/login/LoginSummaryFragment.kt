/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.*
import eu.mikus.edziennik.databinding.LoginSummaryFragmentBinding
import eu.mikus.edziennik.ext.onChange
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.utils.SimpleDividerItemDecoration
import kotlin.coroutines.CoroutineContext

class LoginSummaryFragment : Fragment(), CoroutineScope {
    companion object {
        private const val TAG = "LoginSummaryFragment"
    }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private lateinit var b: LoginSummaryFragmentBinding
    private val nav by lazy { activity.nav }

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    // local/private variables go here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = LoginSummaryFragmentBinding.inflate(inflater)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val adapter = LoginSummaryAdapter(activity) { _ ->
            b.finishButton.isEnabled = activity.profiles.any { it.isSelected }
        }

        adapter.items = activity.profiles
        b.list.adapter = adapter
        b.list.apply {
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(SimpleDividerItemDecoration(context))
        }

        b.anotherButton.onClick {
            nav.navigate(R.id.loginChooserFragment, null, activity.navOptions)
        }

        // Cross-user-sharing registration was removed when SzkolnyApi was
        // dropped from the fork. New profiles are never registered, so the
        // login flow no longer surfaces the toggle and passes no argument
        // to the sync fragment (which defaults to REGISTRATION_DISABLED).
        b.finishButton.onClick {
            nav.navigate(R.id.loginSyncFragment, null, activity.navOptions)
        }
    }
}
