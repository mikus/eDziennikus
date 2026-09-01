/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.announcements

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.AnnouncementGetEvent
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.AnnouncementFull
import eu.mikus.edziennik.databinding.DialogAnnouncementBinding
import eu.mikus.edziennik.databinding.FragmentAnnouncementsBinding
import eu.mikus.edziennik.ui.base.ScreenAction
import eu.mikus.edziennik.ui.base.syncFeature
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AnnouncementsFragment : Fragment() {

    companion object {
        private const val TAG = "AnnouncementsFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: FragmentAnnouncementsBinding? = null
    private lateinit var viewModel: AnnouncementsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = FragmentAnnouncementsBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, AnnouncementsViewModel.Factory)[AnnouncementsViewModel::class.java]

        activity.setScreenActions(listOf(
            ScreenAction(R.string.menu_mark_as_read, CommunityMaterial.Icon.cmd_eye_check_outline) {
                if (app.profile.loginStoreType == LoginType.LIBRUS) {
                    EdziennikTask.announcementsRead(App.profileId).enqueue(requireContext())
                } else {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            App.db.metadataDao().setAllSeen(App.profileId, MetadataType.ANNOUNCEMENT, true)
                        }
                        Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        ))

        b.announcementsCompose.setAppThemeContent {
            val listState = rememberLazyListState()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val refreshing by app.syncStatus.isRefreshing.collectAsStateWithLifecycle()
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { syncFeature(activity, FeatureType.ANNOUNCEMENTS) }) {
                AnnouncementsScreen(
                    state = state,
                    onAnnouncementClick = ::onAnnouncementClick,
                    listState = listState,
                )
            }
        }
    }

    // Controller: the screen's one real coordination responsibility (kept in the fragment).
    private fun onAnnouncementClick(announcement: AnnouncementFull) {
        if (announcement.text == null || (app.profile.loginStoreType == LoginType.LIBRUS && !announcement.seen)) {
            EdziennikTask.announcementGet(App.profileId, announcement).enqueue(requireContext())
        } else {
            showAnnouncementDetailsDialog(announcement)
        }
    }

    private fun showAnnouncementDetailsDialog(announcement: AnnouncementFull) {
        val dialogBinding = DialogAnnouncementBinding.inflate(LayoutInflater.from(activity), null, false)
        MaterialAlertDialogBuilder(activity)
            .setTitle(announcement.subject)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.ok, null)
            .show()
        val start = announcement.startDate?.formattedString ?: "-"
        val dateText = announcement.endDate
            ?.let { getString(R.string.announcement_date_range, start, it.formattedString) }
            ?: start
        dialogBinding.text.text = "${announcement.teacherName}\n\n$dateText\n\n${announcement.text}"
        if (!announcement.seen && app.profile.loginStoreType != LoginType.LIBRUS) {
            announcement.seen = true
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    App.db.metadataDao().setSeen(App.profileId, announcement, true)
                }
            }
        }
    }

    override fun onStart() {
        EventBus.getDefault().register(this)
        super.onStart()
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onAnnouncementGetEvent(event: AnnouncementGetEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        showAnnouncementDetailsDialog(event.announcement)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
