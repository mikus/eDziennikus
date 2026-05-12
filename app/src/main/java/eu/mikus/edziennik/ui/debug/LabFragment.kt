/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-3.
 */

package eu.mikus.edziennik.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.databinding.TemplateFragmentBinding
import eu.mikus.edziennik.ext.addOnPageSelectedListener
import eu.mikus.edziennik.ui.base.lazypager.FragmentLazyPagerAdapter
import eu.mikus.edziennik.ui.login.LoginActivity
import eu.mikus.edziennik.utils.SwipeRefreshLayoutNoTouch
import kotlin.coroutines.CoroutineContext

class LabFragment : Fragment(), CoroutineScope {
    companion object {
        private const val TAG = "LabFragment"
        var pageSelection = 0
    }

    private lateinit var app: App
    private lateinit var activity: AppCompatActivity
    private lateinit var b: TemplateFragmentBinding

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    // local/private variables go here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as AppCompatActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = TemplateFragmentBinding.inflate(inflater)
        when (activity) {
            is MainActivity -> b.refreshLayout.setParent((activity as MainActivity).swipeRefreshLayout)
            is LoginActivity -> b.refreshLayout.setParent((activity as LoginActivity).swipeRefreshLayout)
        }
        b.refreshLayout.isEnabled = false
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!isAdded) return

        val pagerAdapter = FragmentLazyPagerAdapter(
            parentFragmentManager,
                b.refreshLayout,
                listOf(
                        LabPageFragment() to "click me",
                        LabProfileFragment() to "JSON"
                )
        )
        b.viewPager.apply {
            offscreenPageLimit = 1
            adapter = pagerAdapter
            currentItem = pageSelection
            addOnPageSelectedListener {
                pageSelection = it
            }
            b.tabLayout.setupWithViewPager(this)
        }
    }
}
