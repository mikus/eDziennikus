/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.homework

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.databinding.HomeworkFragmentBinding
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.event.EventDetailsDialog
import eu.mikus.edziennik.ui.event.EventManualDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem

class HomeworkFragment : Fragment() {

    companion object {
        private const val TAG = "HomeworkFragment"
        var pageSelection = 0
    }

    private lateinit var activity: MainActivity
    private var b: HomeworkFragmentBinding? = null
    private lateinit var viewModel: HomeworkViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        val binding = HomeworkFragmentBinding.inflate(inflater, container, false)
        b = binding
        binding.refreshLayout.setParent(activity.swipeRefreshLayout)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, HomeworkViewModel.Factory)[HomeworkViewModel::class.java]

        activity.navView.apply {
            bottomBar.apply {
                fabEnable = true
                fabExtendedText = getString(R.string.add)
                fabIcon = CommunityMaterial.Icon3.cmd_plus
            }
            setFabOnClickListener(::onAddClick)
        }
        activity.gainAttention()
        activity.gainAttentionFAB()

        // Defer the bottom-sheet prepend one navlib settle-pass past onViewCreated (Phase-1 navlib-timing fix).
        view.postDelayed({
            if (!isAdded) return@postDelayed
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
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                App.db.metadataDao().setAllSeen(App.profileId, MetadataType.HOMEWORK, true)
                            }
                            Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                        }
                    }),
            )
        }, 100)

        b.homeworkCompose.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            HomeworkScreen(
                state = state,
                onQueryChange = viewModel::setQuery,
                onEventClick = ::onEventClick,
                onEventEditClick = ::onEventEditClick,
                onItemSeen = viewModel::markSeen,
                initialPage = pageSelection,
                onPageChange = { pageSelection = it },
                setRefreshEnabled = { b.refreshLayout.isEnabled = it },
            )
        }
    }

    private fun onAddClick(view: View?) {
        EventManualDialog(activity, App.profileId, defaultType = Event.TYPE_HOMEWORK).show()
    }

    private fun onEventClick(event: EventFull) {
        EventDetailsDialog(activity, event).show()
    }

    private fun onEventEditClick(event: EventFull) {
        EventManualDialog(activity, event.profileId, editingEvent = event).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
