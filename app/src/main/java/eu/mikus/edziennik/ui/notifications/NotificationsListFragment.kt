/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.notifications

import android.app.Activity
import android.content.Intent
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
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.databinding.NotificationsListFragmentBinding
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

class NotificationsListFragment : Fragment() {

    companion object {
        private const val TAG = "NotificationsListFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: NotificationsListFragmentBinding? = null
    private lateinit var viewModel: NotificationsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = NotificationsListFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, NotificationsViewModel.Factory)[NotificationsViewModel::class.java]

        activity.bottomSheet.prependItems(
            BottomSheetPrimaryItem(true)
                .withTitle(R.string.menu_remove_notifications)
                .withIcon(CommunityMaterial.Icon.cmd_delete_sweep_outline)
                .withOnClickListener {
                    activity.bottomSheet.close()
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { App.db.notificationDao().clearAll() }
                        Toast.makeText(activity, R.string.menu_remove_notifications_success, Toast.LENGTH_SHORT).show()
                    }
                }
        )

        b.notificationsCompose.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            NotificationsScreen(state = state, onClick = ::onNotificationClick)
        }
    }

    private fun onNotificationClick(notification: Notification) {
        val intent = Intent("android.intent.action.MAIN")
        notification.fillIntent(intent)
        if (notification.profileId != null && notification.profileId != -1 && notification.profileId != app.profile.id && context is Activity) {
            Toast.makeText(app, app.getString(R.string.toast_changing_profile), Toast.LENGTH_LONG).show()
        }
        app.sendBroadcast(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
