/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-30.
 */

package eu.mikus.edziennik.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.TemplateFragmentBinding
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ext.addOnPageSelectedListener
import eu.mikus.edziennik.ui.base.lazypager.FragmentLazyPagerAdapter
import eu.mikus.edziennik.ui.homework.HomeworkDate
import eu.mikus.edziennik.ui.homework.HomeworkListFragment
import kotlin.coroutines.CoroutineContext

class TemplateFragment : Fragment(), CoroutineScope {
    companion object {
        private const val TAG = "TemplateFragment"
        var pageSelection = 0
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private lateinit var b: TemplateFragmentBinding

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    // local/private variables go here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as MainActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = TemplateFragmentBinding.inflate(inflater)
        b.refreshLayout.setParent(activity.swipeRefreshLayout)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!isAdded) return

        val pagerAdapter = FragmentLazyPagerAdapter(
            parentFragmentManager,
                b.refreshLayout,
                listOf(
                        HomeworkListFragment().apply {
                            arguments = Bundle("homeworkDate" to HomeworkDate.CURRENT)
                        } to getString(R.string.homework_tab_current),

                        HomeworkListFragment().apply {
                            arguments = Bundle("homeworkDate" to HomeworkDate.PAST)
                        } to getString(R.string.homework_tab_past),

                        TemplatePageFragment() to "Pager 0",
                        TemplatePageFragment() to "Pager 1",
                        TemplatePageFragment() to "Pager 2",
                        TemplatePageFragment() to "Pager 3",
                        TemplateListPageFragment() to "Pager 4",
                        TemplateListPageFragment() to "Pager 5",
                        TemplateListPageFragment() to "Pager 6",
                        TemplateListPageFragment() to "Pager 7"
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
