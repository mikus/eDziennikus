/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-30.
 */

package eu.mikus.edziennik.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.databinding.TemplatePageFragmentBinding
import eu.mikus.edziennik.ui.base.lazypager.LazyFragment
import kotlin.coroutines.CoroutineContext

class TemplatePageFragment : LazyFragment(), CoroutineScope {
    companion object {
        private const val TAG = "TemplatePagerFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private lateinit var b: TemplatePageFragmentBinding

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    // local/private variables go here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as MainActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = TemplatePageFragmentBinding.inflate(inflater)
        return b.root
    }

    override fun onPageCreated(): Boolean {
        b.text.text = "Fragment $position"

        b.button.addOnCheckedChangeListener { button, isChecked ->
            setSwipeToRefresh(isChecked)
        }
        return true
    }
}
