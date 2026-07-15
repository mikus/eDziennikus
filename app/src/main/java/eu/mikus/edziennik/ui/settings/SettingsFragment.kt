/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.models.Update
import eu.mikus.edziennik.databinding.SettingsFragmentBinding
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.AgendaConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.AppLanguageDialog
import eu.mikus.edziennik.ui.dialogs.settings.AttendanceConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.BellSyncConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.GradesConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.MessagesConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.MiniMenuConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.NotificationFilterDialog
import eu.mikus.edziennik.ui.dialogs.settings.ProfileConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.QuietHoursConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.SyncIntervalDialog
import eu.mikus.edziennik.ui.dialogs.settings.ThemeChooserDialog
import eu.mikus.edziennik.ui.dialogs.settings.TimetableConfigDialog
import eu.mikus.edziennik.ui.login.LoginActivity
import eu.mikus.edziennik.sync.SyncWorker
import eu.mikus.edziennik.sync.UpdateWorker
import eu.mikus.edziennik.utils.Utils
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: SettingsFragmentBinding? = null
    private lateinit var viewModel: SettingsViewModel
    private val mediaPlayer by lazy { MediaPlayer.create(activity, R.raw.ogarnij_sie) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = SettingsFragmentBinding.inflate(inflater, container, false)
        b = binding
        binding.refreshLayout.setParent(activity.swipeRefreshLayout)
        binding.refreshLayout.isEnabled = false      // Settings never pull-to-refreshes
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, SettingsViewModel.Factory(app))[SettingsViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect(::handleEffect)
            }
        }

        b.composeView.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val avatar = remember(state) { app.profile.getImageDrawable(activity) }
            SettingsScreen(
                state = state,
                onToggle = viewModel::onToggle,
                onAction = ::handleAction,
                profileAvatar = avatar,
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }

    private fun handleEffect(effect: SettingsEffect) = when (effect) {
        SettingsEffect.Recreate -> activity.recreate()
        SettingsEffect.RescheduleSync -> SyncWorker.rescheduleNext(app)
        SettingsEffect.RescheduleUpdate -> UpdateWorker.rescheduleNext(app)
        SettingsEffect.RefreshDrawer -> {
            activity.navView.drawer.miniDrawerVisiblePortrait = app.config.ui.miniMenuVisible
        }
    }

    private fun handleAction(action: SettingsAction) {
        when (action) {
            SettingsAction.EditProfile ->
                ProfileConfigDialog(activity, app.profile, onProfileSaved = { viewModel.refresh() }).show()
            SettingsAction.AddStudent -> activity.startActivity(Intent(activity, LoginActivity::class.java))

            SettingsAction.Theme -> ThemeChooserDialog(activity, onDismissListener = { viewModel.refresh() }).show()
            SettingsAction.Language -> AppLanguageDialog(activity).show()
            SettingsAction.MiniMenuButtons -> MiniMenuConfigDialog(activity).show()
            SettingsAction.HeaderBackground -> chooseHeaderBackground()
            SettingsAction.AppBackground -> chooseAppBackground()

            SettingsAction.SyncInterval -> SyncIntervalDialog(activity, onChangeListener = { viewModel.refresh() }).show()
            SettingsAction.QuietHours -> QuietHoursConfigDialog(activity, onChangeListener = { viewModel.refresh() })
            SettingsAction.NotificationFilter -> NotificationFilterDialog(activity).show()
            SettingsAction.NotificationSystem -> openNotificationSystemSettings()

            SettingsAction.TimetableConfig -> TimetableConfigDialog(activity, reloadOnDismiss = false, onDismissListener = { viewModel.refresh() }).show()
            SettingsAction.AgendaConfig -> AgendaConfigDialog(activity, reloadOnDismiss = false, onDismissListener = { viewModel.refresh() }).show()
            SettingsAction.GradesConfig -> GradesConfigDialog(activity, reloadOnDismiss = false, onDismissListener = { viewModel.refresh() }).show()
            SettingsAction.MessagesConfig -> MessagesConfigDialog(activity, reloadOnDismiss = false, onDismissListener = { viewModel.refresh() }).show()
            SettingsAction.AttendanceConfig -> AttendanceConfigDialog(activity, reloadOnDismiss = false, onDismissListener = { viewModel.refresh() }).show()
            SettingsAction.BellSync -> BellSyncConfigDialog(activity, onChangeListener = { viewModel.refresh() }).show()

            SettingsAction.VersionTap -> Toast.makeText(activity, "😂", Toast.LENGTH_SHORT).show()
            SettingsAction.VersionEasterEgg -> mediaPlayer.start()
            SettingsAction.VersionDetails -> app.buildManager.showVersionDialog(activity)
            SettingsAction.Changelog -> Utils.openUrl(activity, "https://github.com/mikus/eDziennikus/releases")
            SettingsAction.CheckUpdate -> checkForUpdate()
            SettingsAction.Privacy -> Utils.openUrl(activity, "https://github.com/mikus/eDziennikus/blob/main/PRIVACY.md")
            SettingsAction.Github -> Utils.openUrl(activity, "https://github.com/mikus/eDziennikus")
            SettingsAction.Licenses -> activity.startActivity(Intent(activity, LicensesActivity::class.java))
            SettingsAction.Crash -> throw RuntimeException("MANUAL CRASH")
        }
    }

    private fun chooseHeaderBackground() {
        if (app.config.ui.headerBackground == null) { setHeaderBackground(); return }
        MaterialAlertDialogBuilder(activity)
            .setItems(
                arrayOf(
                    activity.getString(R.string.settings_theme_drawer_header_dialog_set),
                    activity.getString(R.string.settings_theme_drawer_header_dialog_restore),
                ),
            ) { _, which ->
                when (which) {
                    0 -> setHeaderBackground()
                    1 -> {
                        app.config.ui.headerBackground = null
                        activity.drawer.setAccountHeaderBackground(null)
                        activity.drawer.open()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setHeaderBackground() = activity.requestHandler.requestHeaderBackground {
        activity.drawer.setAccountHeaderBackground(null)
        activity.drawer.setAccountHeaderBackground(app.config.ui.headerBackground)
        activity.drawer.open()
    }

    private fun chooseAppBackground() {
        if (app.config.ui.appBackground == null) { setAppBackground(); return }
        MaterialAlertDialogBuilder(activity)
            .setItems(
                arrayOf(
                    activity.getString(R.string.settings_theme_app_background_dialog_set),
                    activity.getString(R.string.settings_theme_app_background_dialog_restore),
                ),
            ) { _, which ->
                when (which) {
                    0 -> setAppBackground()
                    1 -> {
                        app.config.ui.appBackground = null
                        activity.setAppBackground()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setAppBackground() = activity.requestHandler.requestAppBackground { activity.setAppBackground() }

    private fun openNotificationSystemSettings() {
        val channel = app.notificationChannelsManager.data.key
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channel)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", app.packageName)
                    putExtra("app_uid", app.applicationInfo.uid)
                }
                else -> {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    addCategory(Intent.CATEGORY_DEFAULT)
                    data = Uri.parse("package:" + app.packageName)
                }
            }
        }
        activity.startActivity(intent)
    }

    private fun checkForUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val channel = if (App.devMode) Update.Type.BETA else Update.Type.RC
            val result = app.updateManager.checkNow(channel, notify = false)
            val update = result.getOrNull()
            when {
                result.isFailure -> Toast.makeText(app, app.getString(R.string.notification_cant_check_update), Toast.LENGTH_SHORT).show()
                update == null -> Toast.makeText(app, app.getString(R.string.notification_no_update), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
