/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-18.
 */

package eu.mikus.edziennik.ui.settings.cards

import com.danielstone.materialaboutlibrary.items.MaterialAboutItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.after
import eu.mikus.edziennik.ext.getStudentData
import eu.mikus.edziennik.ext.hasUIFeature
import eu.mikus.edziennik.ext.set
import eu.mikus.edziennik.ui.dialogs.settings.AgendaConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.AttendanceConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.BellSyncConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.GradesConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.MessagesConfigDialog
import eu.mikus.edziennik.ui.dialogs.settings.TimetableConfigDialog
import eu.mikus.edziennik.ui.settings.SettingsCard
import eu.mikus.edziennik.ui.settings.SettingsUtil

class SettingsRegisterCard(util: SettingsUtil) : SettingsCard(util) {

    override fun buildCard() = util.createCard(
        R.string.settings_card_register_title,
        items = ::getItems,
        itemsMore = ::getItemsMore,
    )

    private fun getBellSync() =
        configGlobal.timetable.bellSyncDiff?.let {
            activity.getString(
                R.string.settings_register_bell_sync_subtext_format,
                (if (configGlobal.timetable.bellSyncMultiplier == -1) "-" else "+") + it.stringHMS
            )
        } ?: activity.getString(R.string.settings_register_bell_sync_subtext_disabled)

    override fun getItems(card: MaterialAboutCard) = listOfNotNull(
        util.createActionItem(
            text = R.string.menu_timetable_config,
            icon = CommunityMaterial.Icon3.cmd_timetable
        ) {
            TimetableConfigDialog(activity, reloadOnDismiss = false).show()
        }.takeIf { app.profile.hasUIFeature(FeatureType.TIMETABLE) },

        util.createActionItem(
            text = R.string.menu_agenda_config,
            icon = CommunityMaterial.Icon.cmd_calendar_outline
        ) {
            AgendaConfigDialog(activity, reloadOnDismiss = false).show()
        }.takeIf { app.profile.hasUIFeature(FeatureType.AGENDA) },

        util.createActionItem(
            text = R.string.menu_grades_config,
            icon = CommunityMaterial.Icon3.cmd_numeric_5_box_outline
        ) {
            GradesConfigDialog(activity, reloadOnDismiss = false).show()
        }.takeIf { app.profile.hasUIFeature(FeatureType.GRADES) },

        util.createActionItem(
            text = R.string.menu_messages_config,
            icon = CommunityMaterial.Icon.cmd_email_outline
        ) {
            MessagesConfigDialog(activity, reloadOnDismiss = false).show()
        }.takeIf {
            app.profile.hasUIFeature(FeatureType.MESSAGES_INBOX) || app.profile.hasUIFeature(
                FeatureType.MESSAGES_SENT)
        },

        util.createActionItem(
            text = R.string.menu_attendance_config,
            icon = CommunityMaterial.Icon.cmd_calendar_remove_outline
        ) {
            AttendanceConfigDialog(activity, reloadOnDismiss = false).show()
        }.takeIf { app.profile.hasUIFeature(FeatureType.ATTENDANCE) },

        util.createMoreItem(
            card = card,
            items = listOfNotNull(
                util.createActionItem(
                    text = R.string.settings_register_bell_sync_text,
                    icon = SzkolnyFont.Icon.szf_alarm_bell_outline,
                    onClick = {
                        BellSyncConfigDialog(activity, onChangeListener = {
                            it.subText = getBellSync()
                            util.refresh()
                        }).show()
                    }
                ).also {
                    it.subText = getBellSync()
                },

                util.createPropertyItem(
                    text = R.string.settings_register_count_in_seconds_text,
                    subText = R.string.settings_register_count_in_seconds_subtext,
                    icon = CommunityMaterial.Icon3.cmd_timer_outline,
                    value = configGlobal.timetable.countInSeconds
                ) { _, it ->
                    configGlobal.timetable.countInSeconds = it
                },

                util.createPropertyItem(
                    text = R.string.settings_register_show_teacher_absences_text,
                    icon = CommunityMaterial.Icon.cmd_account_arrow_right_outline,
                    value = app.profile.getStudentData("showTeacherAbsences", true)
                ) { _, it ->
                    app.profile["showTeacherAbsences"] = it
                    app.profileSave()
                }.takeIf { app.profile.loginStoreType == LoginType.LIBRUS },

                util.createPropertyItem(
                    text = R.string.settings_register_hide_sticks_from_old,
                    icon = CommunityMaterial.Icon3.cmd_numeric_1_box_outline,
                    value = configProfile.grades.hideSticksFromOld
                ) { _, it ->
                    configProfile.grades.hideSticksFromOld = it
                }.takeIf { App.devMode && app.profile.hasUIFeature(FeatureType.GRADES) },
            ),
        ),

        *(getRegistrationItems().takeIf { !app.profile.archived } ?: arrayOf()),
    )

    // Cross-user-sharing registration was removed when SzkolnyApi was
    // dropped from the fork. The "Allow registration" toggle and the
    // dependent "Share by default" item used to live here; both UI
    // affordances have been removed because no backend can act on them.
    // Returning an empty array keeps the surrounding card structure
    // (settings_card_register_title remains for the e-diary settings
    // above).
    private fun getRegistrationItems() = emptyArray<MaterialAboutItem>()
}
