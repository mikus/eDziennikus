/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-30.
 */

package eu.mikus.edziennik.ui.homework

import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.databinding.HomeworkFragmentBinding
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ext.addOnPageSelectedListener
import eu.mikus.edziennik.ui.base.lazypager.FragmentLazyPagerAdapter
import eu.mikus.edziennik.ui.event.EventManualDialog
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem
import kotlin.coroutines.CoroutineContext

class HomeworkFragment : Fragment(), CoroutineScope {
    companion object {
        private const val TAG = "HomeworkFragment"
        var pageSelection = 0
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private lateinit var b: HomeworkFragmentBinding

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    // local/private variables go here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as MainActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = HomeworkFragmentBinding.inflate(inflater)
        b.refreshLayout.setParent(activity.swipeRefreshLayout)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!isAdded) return

        activity.bottomSheet.prependItems(
                BottomSheetPrimaryItem(true)
                .withTitle(R.string.menu_add_event)
                .withDescription(R.string.menu_add_event_desc)
                .withIcon(SzkolnyFont.Icon.szf_calendar_plus_outline)
                .withOnClickListener(View.OnClickListener {
                    activity.bottomSheet.close()
                    EventManualDialog(activity, App.profileId, defaultType = Event.TYPE_HOMEWORK).show()
                }),
        BottomSheetSeparatorItem(true),
        BottomSheetPrimaryItem(true)
                .withTitle(R.string.menu_mark_as_read)
                .withIcon(CommunityMaterial.Icon.cmd_eye_check_outline)
                .withOnClickListener(View.OnClickListener {
                    activity.bottomSheet.close()
                    AsyncTask.execute { app.db.metadataDao().setAllSeen(App.profileId, MetadataType.HOMEWORK, true) }
                    Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                }))

        val pagerAdapter = FragmentLazyPagerAdapter(
            parentFragmentManager,
                b.refreshLayout,
                listOf(
                        HomeworkListFragment().apply {
                            arguments = Bundle("homeworkDate" to HomeworkDate.CURRENT)
                        } to getString(R.string.homework_tab_current),

                        HomeworkListFragment().apply {
                            arguments = Bundle("homeworkDate" to HomeworkDate.PAST)
                        } to getString(R.string.homework_tab_past)
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

        activity.navView.apply {
            bottomBar.apply {
                fabEnable = true
                fabExtendedText = getString(R.string.add)
                fabIcon = CommunityMaterial.Icon3.cmd_plus
            }

            setFabOnClickListener(View.OnClickListener {
                EventManualDialog(activity, App.profileId, defaultType = Event.TYPE_HOMEWORK).show()
            })
        }

        activity.gainAttention()
        activity.gainAttentionFAB()
    }
}
