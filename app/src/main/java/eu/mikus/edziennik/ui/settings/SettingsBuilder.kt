/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.szkolny.font.SzkolnyFont

/** Pure, Android-free assembly of the 5 Settings cards (gating + dynamic subtexts). */
object SettingsBuilder {

    fun build(s: SettingsSnapshot): List<SettingsCardUi> = listOf(
        profileCard(s), themeCard(s), syncCard(s), registerCard(s), aboutCard(s),
    )

    private fun profileCard(s: SettingsSnapshot) = SettingsCardUi(
        titleRes = null,
        items = listOf(
            SettingsItem.Profile(name = s.profileName, subname = s.profileSubname),
            SettingsItem.Action(
                textRes = R.string.settings_add_student_text,
                subTextRes = R.string.settings_add_student_subtext,
                icon = CommunityMaterial.Icon.cmd_account_plus_outline,
                action = SettingsAction.AddStudent,
            ),
        ),
    )

    private fun themeCard(s: SettingsSnapshot) = SettingsCardUi(
        titleRes = R.string.settings_card_theme_title,
        items = buildList {
            if (s.snowfallWindow) add(
                SettingsItem.Switch(
                    R.string.settings_theme_snowfall_text, R.string.settings_theme_snowfall_subtext,
                    CommunityMaterial.Icon3.cmd_snowflake, s.snowfall, SettingsToggle.SNOWFALL,
                ),
            )
            if (s.eggfallNear) add(
                SettingsItem.Switch(
                    R.string.settings_theme_eggfall_text, R.string.settings_theme_eggfall_subtext,
                    CommunityMaterial.Icon.cmd_egg_easter, s.eggfall, SettingsToggle.EGGFALL,
                ),
            )
            add(
                SettingsItem.Action(
                    textRes = R.string.settings_theme_theme_text, subText = s.themeName,
                    icon = CommunityMaterial.Icon3.cmd_palette_outline, action = SettingsAction.Theme,
                ),
            )
            add(
                SettingsItem.Action(
                    textRes = R.string.settings_about_language_text, subTextRes = R.string.settings_about_language_subtext,
                    icon = CommunityMaterial.Icon3.cmd_translate, action = SettingsAction.Language,
                ),
            )
            add(
                SettingsItem.Switch(
                    R.string.settings_theme_mini_drawer_text, R.string.settings_theme_mini_drawer_subtext,
                    CommunityMaterial.Icon.cmd_dots_vertical, s.miniDrawer, SettingsToggle.MINI_DRAWER,
                ),
            )
            add(
                SettingsItem.More(
                    listOf(
                        SettingsItem.Action(
                            textRes = R.string.settings_theme_mini_drawer_buttons_text,
                            icon = CommunityMaterial.Icon2.cmd_format_list_checks, action = SettingsAction.MiniMenuButtons,
                        ),
                        SettingsItem.Action(
                            textRes = R.string.settings_theme_drawer_header_text,
                            icon = CommunityMaterial.Icon2.cmd_image_outline, action = SettingsAction.HeaderBackground,
                        ),
                        SettingsItem.Action(
                            textRes = R.string.settings_theme_app_background_text,
                            subTextRes = R.string.settings_theme_app_background_subtext,
                            icon = CommunityMaterial.Icon2.cmd_image_filter_hdr, action = SettingsAction.AppBackground,
                        ),
                        SettingsItem.Switch(
                            R.string.settings_theme_open_drawer_on_back_pressed_text, null,
                            CommunityMaterial.Icon3.cmd_menu_open, s.openDrawerOnBack, SettingsToggle.OPEN_DRAWER_ON_BACK,
                        ),
                    ),
                ),
            )
        },
    )

    private fun syncCard(s: SettingsSnapshot) = SettingsCardUi(
        titleRes = R.string.settings_card_sync_title,
        items = buildList {
            add(
                SettingsItem.ActionSwitch(
                    textRes = R.string.settings_sync_sync_interval_text,
                    subTextDisabledRes = R.string.settings_sync_sync_interval_subtext_disabled,
                    subTextChecked = s.syncInterval, icon = CommunityMaterial.Icon.cmd_download_outline,
                    checked = s.syncEnabled, toggle = SettingsToggle.SYNC_ENABLED, action = SettingsAction.SyncInterval,
                ),
            )
            if (s.syncEnabled) add(
                SettingsItem.Switch(
                    R.string.settings_sync_wifi_text, R.string.settings_sync_wifi_subtext,
                    CommunityMaterial.Icon3.cmd_wifi_strength_2, s.onlyWifi, SettingsToggle.SYNC_ONLY_WIFI,
                ),
            )
            add(
                SettingsItem.Action(
                    textRes = R.string.settings_profile_notifications_text, subTextRes = R.string.settings_profile_notifications_subtext,
                    icon = CommunityMaterial.Icon2.cmd_filter_outline, action = SettingsAction.NotificationFilter,
                ),
            )
            add(
                SettingsItem.ActionSwitch(
                    textRes = R.string.settings_sync_quiet_hours_text,
                    subTextDisabledRes = R.string.settings_sync_quiet_hours_subtext_disabled,
                    subTextChecked = s.quietHours, icon = CommunityMaterial.Icon.cmd_bell_sleep_outline,
                    checked = s.quietHoursEnabled, toggle = SettingsToggle.QUIET_HOURS, action = SettingsAction.QuietHours,
                ),
            )
            add(
                SettingsItem.More(
                    buildList {
                        add(
                            SettingsItem.Switch(
                                R.string.settings_sync_updates_text, null,
                                CommunityMaterial.Icon.cmd_cellphone_arrow_down, s.notifyUpdates, SettingsToggle.NOTIFY_UPDATES,
                            ),
                        )
                        if (s.sdkAtLeastKitKat) add(
                            SettingsItem.Action(
                                textRes = R.string.settings_sync_notifications_settings_text,
                                subTextRes = R.string.settings_sync_notifications_settings_subtext,
                                icon = CommunityMaterial.Icon.cmd_cog_outline, action = SettingsAction.NotificationSystem,
                            ),
                        )
                    },
                ),
            )
        },
    )

    private fun registerCard(s: SettingsSnapshot) = SettingsCardUi(
        titleRes = R.string.settings_card_register_title,
        items = buildList {
            if (s.hasTimetable) add(
                SettingsItem.Action(R.string.menu_timetable_config, icon = CommunityMaterial.Icon3.cmd_timetable, action = SettingsAction.TimetableConfig),
            )
            if (s.hasAgenda) add(
                SettingsItem.Action(R.string.menu_agenda_config, icon = CommunityMaterial.Icon.cmd_calendar_outline, action = SettingsAction.AgendaConfig),
            )
            if (s.hasGrades) add(
                SettingsItem.Action(R.string.menu_grades_config, icon = CommunityMaterial.Icon3.cmd_numeric_5_box_outline, action = SettingsAction.GradesConfig),
            )
            if (s.hasMessages) add(
                SettingsItem.Action(R.string.menu_messages_config, icon = CommunityMaterial.Icon.cmd_email_outline, action = SettingsAction.MessagesConfig),
            )
            if (s.hasAttendance) add(
                SettingsItem.Action(R.string.menu_attendance_config, icon = CommunityMaterial.Icon.cmd_calendar_remove_outline, action = SettingsAction.AttendanceConfig),
            )
            add(
                SettingsItem.More(
                    buildList {
                        add(
                            SettingsItem.Action(
                                textRes = R.string.settings_register_bell_sync_text, subText = s.bellSync,
                                icon = SzkolnyFont.Icon.szf_alarm_bell_outline, action = SettingsAction.BellSync,
                            ),
                        )
                        add(
                            SettingsItem.Switch(
                                R.string.settings_register_count_in_seconds_text, R.string.settings_register_count_in_seconds_subtext,
                                CommunityMaterial.Icon3.cmd_timer_outline, s.countInSeconds, SettingsToggle.COUNT_IN_SECONDS,
                            ),
                        )
                        if (s.isLibrus) add(
                            SettingsItem.Switch(
                                R.string.settings_register_show_teacher_absences_text, null,
                                CommunityMaterial.Icon.cmd_account_arrow_right_outline, s.showTeacherAbsences, SettingsToggle.SHOW_TEACHER_ABSENCES,
                            ),
                        )
                        if (s.devMode && s.hasGrades) add(
                            SettingsItem.Switch(
                                R.string.settings_register_hide_sticks_from_old, null,
                                CommunityMaterial.Icon3.cmd_numeric_1_box_outline, s.hideSticksFromOld, SettingsToggle.HIDE_STICKS_FROM_OLD,
                            ),
                        )
                    },
                ),
            )
        },
    )

    private fun aboutCard(s: SettingsSnapshot) = SettingsCardUi(
        titleRes = null,
        style = CardStyle.AboutBlueDark,
        items = buildList {
            add(SettingsItem.Title(R.mipmap.ic_launcher, R.string.app_name, R.string.settings_about_title_subtext))
            add(
                SettingsItem.Action(
                    textRes = R.string.settings_about_version_text, subText = s.versionText,
                    icon = CommunityMaterial.Icon2.cmd_information_outline, action = SettingsAction.VersionTap,
                ),
            )
            add(
                SettingsItem.More(
                    listOf(
                        SettingsItem.Action(R.string.settings_about_changelog_text, icon = CommunityMaterial.Icon3.cmd_radar, action = SettingsAction.Changelog),
                        SettingsItem.Action(
                            textRes = R.string.settings_about_update_text, subTextRes = R.string.settings_about_update_subtext,
                            icon = CommunityMaterial.Icon3.cmd_update, action = SettingsAction.CheckUpdate,
                        ),
                    ),
                ),
            )
            add(SettingsItem.Section(R.string.see_also))
            add(SettingsItem.Action(R.string.settings_about_privacy_policy_text, icon = CommunityMaterial.Icon3.cmd_shield_outline, action = SettingsAction.Privacy))
            add(
                SettingsItem.Action(
                    textRes = R.string.settings_about_github_text, subTextRes = R.string.settings_about_github_subtext,
                    icon = SzkolnyFont.Icon.szf_github_face, action = SettingsAction.Github,
                ),
            )
            add(
                SettingsItem.More(
                    buildList {
                        add(SettingsItem.Action(R.string.settings_about_licenses_text, icon = CommunityMaterial.Icon.cmd_code_braces, action = SettingsAction.Licenses))
                        if (s.devMode) add(
                            SettingsItem.Action(
                                textRes = R.string.settings_about_crash_text, subTextRes = R.string.settings_about_crash_subtext,
                                icon = CommunityMaterial.Icon.cmd_bug_outline, action = SettingsAction.Crash,
                            ),
                        )
                    },
                ),
            )
        },
    )
}
