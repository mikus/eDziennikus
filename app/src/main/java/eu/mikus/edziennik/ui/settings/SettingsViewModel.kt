/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import eu.mikus.edziennik.App
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.R
import eu.mikus.edziennik.config.Config
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.getStudentData
import eu.mikus.edziennik.ext.getSyncInterval
import eu.mikus.edziennik.ext.hasUIFeature
import eu.mikus.edziennik.ext.set
import eu.mikus.edziennik.utils.BigNightUtil
import eu.mikus.edziennik.utils.Themes
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val buildSnapshot: () -> SettingsSnapshot,
    private val writeToggle: (SettingsToggle, Boolean) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(SettingsBuilder.build(buildSnapshot())))
    val uiState: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    fun onToggle(toggle: SettingsToggle, value: Boolean) {
        writeToggle(toggle, value)
        refresh()
        effectFor(toggle)?.let { _effects.tryEmit(it) }
    }

    /** Re-snapshots config and rebuilds state. Also called by the host on dialog dismiss. */
    fun refresh() {
        _state.value = SettingsUiState(SettingsBuilder.build(buildSnapshot()))
    }

    private fun effectFor(toggle: SettingsToggle): SettingsEffect? = when (toggle) {
        SettingsToggle.SNOWFALL, SettingsToggle.EGGFALL -> SettingsEffect.Recreate
        SettingsToggle.MINI_DRAWER -> SettingsEffect.RefreshDrawer
        SettingsToggle.SYNC_ENABLED, SettingsToggle.SYNC_ONLY_WIFI -> SettingsEffect.RescheduleSync
        SettingsToggle.NOTIFY_UPDATES -> SettingsEffect.RescheduleUpdate
        SettingsToggle.OPEN_DRAWER_ON_BACK, SettingsToggle.QUIET_HOURS,
        SettingsToggle.COUNT_IN_SECONDS, SettingsToggle.SHOW_TEACHER_ABSENCES,
        SettingsToggle.HIDE_STICKS_FROM_OLD -> null
    }

    class Factory(private val app: App) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                buildSnapshot = { readSettingsSnapshot(app) },
                writeToggle = { toggle, value -> writeSettingsToggle(app, toggle, value) },
            ) as T
        }
    }
}

// --- Android edge: the only App/Android readers ---

internal fun readSettingsSnapshot(app: App): SettingsSnapshot {
    val cfg = app.config
    val profile = app.profile
    return SettingsSnapshot(
        profileName = profile.name,
        profileSubname = profile.subname,
        snowfallWindow = Date.getToday().month / 3 % 4 == 0,
        snowfall = cfg.ui.snowfall,
        eggfallNear = BigNightUtil().isDataWielkanocyNearDzisiaj(),
        eggfall = cfg.ui.eggfall,
        themeName = app.getString(Themes.getThemeNameRes()),
        miniDrawer = cfg.ui.miniMenuVisible,
        openDrawerOnBack = cfg.ui.openDrawerOnBackPressed,
        syncEnabled = cfg.sync.enabled,
        syncInterval = app.getSyncInterval(cfg.sync.interval),
        onlyWifi = cfg.sync.onlyWifi,
        quietHoursEnabled = cfg.sync.quietHoursEnabled,
        quietHours = quietHoursSummary(app, cfg),
        notifyUpdates = cfg.sync.notifyAboutUpdates,
        sdkAtLeastKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT,
        hasTimetable = profile.hasUIFeature(FeatureType.TIMETABLE),
        hasAgenda = profile.hasUIFeature(FeatureType.AGENDA),
        hasGrades = profile.hasUIFeature(FeatureType.GRADES),
        hasMessages = profile.hasUIFeature(FeatureType.MESSAGES_INBOX) || profile.hasUIFeature(FeatureType.MESSAGES_SENT),
        hasAttendance = profile.hasUIFeature(FeatureType.ATTENDANCE),
        bellSync = bellSyncSummary(app, cfg),
        countInSeconds = cfg.timetable.countInSeconds,
        isLibrus = profile.loginStoreType == LoginType.LIBRUS,
        showTeacherAbsences = profile.getStudentData("showTeacherAbsences", true),
        devMode = App.devMode,
        hideSticksFromOld = profile.config.grades.hideSticksFromOld,
        versionText = BuildConfig.VERSION_NAME + ", " + BuildConfig.BUILD_TYPE,
    )
}

internal fun writeSettingsToggle(app: App, toggle: SettingsToggle, value: Boolean) {
    val cfg = app.config
    when (toggle) {
        SettingsToggle.SNOWFALL -> cfg.ui.snowfall = value
        SettingsToggle.EGGFALL -> cfg.ui.eggfall = value
        SettingsToggle.MINI_DRAWER -> cfg.ui.miniMenuVisible = value
        SettingsToggle.OPEN_DRAWER_ON_BACK -> cfg.ui.openDrawerOnBackPressed = value
        SettingsToggle.SYNC_ENABLED -> cfg.sync.enabled = value
        SettingsToggle.SYNC_ONLY_WIFI -> cfg.sync.onlyWifi = value
        SettingsToggle.QUIET_HOURS -> cfg.sync.quietHoursEnabled = value
        SettingsToggle.NOTIFY_UPDATES -> cfg.sync.notifyAboutUpdates = value
        SettingsToggle.COUNT_IN_SECONDS -> cfg.timetable.countInSeconds = value
        SettingsToggle.SHOW_TEACHER_ABSENCES -> {
            app.profile["showTeacherAbsences"] = value
            app.profileSave()
        }
        SettingsToggle.HIDE_STICKS_FROM_OLD -> app.profile.config.grades.hideSticksFromOld = value
    }
}

private fun quietHoursSummary(app: App, cfg: Config): String {
    if (cfg.sync.quietHoursStart == null) cfg.sync.quietHoursStart = Time(22, 30, 0)
    if (cfg.sync.quietHoursEnd == null) cfg.sync.quietHoursEnd = Time(6, 30, 0)
    return app.getString(
        if (cfg.sync.quietHoursStart!! > cfg.sync.quietHoursEnd!!)
            R.string.settings_sync_quiet_hours_subtext_next_day_format
        else
            R.string.settings_sync_quiet_hours_subtext_format,
        cfg.sync.quietHoursStart?.stringHM,
        cfg.sync.quietHoursEnd?.stringHM,
    )
}

private fun bellSyncSummary(app: App, cfg: Config): String =
    cfg.timetable.bellSyncDiff?.let {
        app.getString(
            R.string.settings_register_bell_sync_subtext_format,
            (if (cfg.timetable.bellSyncMultiplier == -1) "-" else "+") + it.stringHMS,
        )
    } ?: app.getString(R.string.settings_register_bell_sync_subtext_disabled)
