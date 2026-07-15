/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import androidx.annotation.StringRes
import com.mikepenz.iconics.typeface.IIcon

data class SettingsUiState(val cards: List<SettingsCardUi>)

enum class CardStyle { Default, AboutBlueDark }

data class SettingsCardUi(
    @StringRes val titleRes: Int?,      // null = no header (Profile, About)
    val items: List<SettingsItem>,
    val style: CardStyle = CardStyle.Default,
)

sealed interface SettingsItem {
    /** About header: app icon (mipmap) + app name + tagline. */
    data class Title(val iconRes: Int, @StringRes val titleRes: Int, @StringRes val subTextRes: Int) : SettingsItem

    data class Section(@StringRes val textRes: Int) : SettingsItem

    /** Custom profile row; avatar is host-resolved at render (not in the Android-free builder output). */
    data class Profile(val name: String, val subname: String?) : SettingsItem

    data class Action(
        @StringRes val textRes: Int,
        @StringRes val subTextRes: Int? = null,   // static subtext
        val subText: String? = null,              // dynamic subtext (wins over subTextRes)
        val icon: IIcon,
        val action: SettingsAction,
    ) : SettingsItem

    data class Switch(
        @StringRes val textRes: Int,
        @StringRes val subTextRes: Int? = null,
        val icon: IIcon,
        val checked: Boolean,
        val toggle: SettingsToggle,
    ) : SettingsItem

    /** Row body opens [action]; trailing switch flips [toggle]. Sync-interval + quiet-hours. */
    data class ActionSwitch(
        @StringRes val textRes: Int,
        @StringRes val subTextDisabledRes: Int,   // shown when unchecked
        val subTextChecked: String,               // dynamic, shown when checked
        val icon: IIcon,
        val checked: Boolean,
        val toggle: SettingsToggle,
        val action: SettingsAction,
    ) : SettingsItem

    /** Collapsible group; expansion is composable-local state. */
    data class More(val items: List<SettingsItem>) : SettingsItem
}

enum class SettingsToggle {
    SNOWFALL, EGGFALL, MINI_DRAWER, OPEN_DRAWER_ON_BACK,
    SYNC_ENABLED, SYNC_ONLY_WIFI, QUIET_HOURS, NOTIFY_UPDATES,
    COUNT_IN_SECONDS, SHOW_TEACHER_ABSENCES, HIDE_STICKS_FROM_OLD,
}

/** Tag carried by an item; the host's handleAction opens a dialog / intent / picker. */
enum class SettingsAction {
    // Profile
    EditProfile, AddStudent,
    // Theme
    Theme, Language, MiniMenuButtons, HeaderBackground, AppBackground,
    // Sync
    SyncInterval, QuietHours, NotificationFilter, NotificationSystem,
    // Register
    TimetableConfig, AgendaConfig, GradesConfig, MessagesConfig, AttendanceConfig, BellSync,
    // About
    VersionTap, VersionEasterEgg, VersionDetails, Changelog, CheckUpdate, Privacy, Github, Licenses, Crash,
}

/** One-shot effects the VM emits and the host performs. */
enum class SettingsEffect { Recreate, RescheduleSync, RescheduleUpdate, RefreshDrawer }

/** Immutable input to [SettingsBuilder]. Produced by the Factory-injected edge lambda. */
data class SettingsSnapshot(
    // Profile
    val profileName: String,
    val profileSubname: String?,
    // Theme
    val snowfallWindow: Boolean,
    val snowfall: Boolean,
    val eggfallNear: Boolean,
    val eggfall: Boolean,
    val themeName: String,
    val miniDrawer: Boolean,
    val openDrawerOnBack: Boolean,
    // Sync
    val syncEnabled: Boolean,
    val syncInterval: String,
    val onlyWifi: Boolean,
    val quietHoursEnabled: Boolean,
    val quietHours: String,
    val notifyUpdates: Boolean,
    val sdkAtLeastKitKat: Boolean,
    // Register
    val hasTimetable: Boolean,
    val hasAgenda: Boolean,
    val hasGrades: Boolean,
    val hasMessages: Boolean,
    val hasAttendance: Boolean,
    val bellSync: String,
    val countInSeconds: Boolean,
    val isLibrus: Boolean,
    val showTeacherAbsences: Boolean,
    val devMode: Boolean,
    val hideSticksFromOld: Boolean,
    // About
    val versionText: String,
)
