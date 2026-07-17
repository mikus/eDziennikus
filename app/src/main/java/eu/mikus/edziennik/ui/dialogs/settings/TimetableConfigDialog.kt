/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.ext.Intent
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.timetable.TimetableFragment

class TimetableConfigDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "TimetableConfigDialog"
    override fun getTitleRes() = R.string.menu_timetable_config
    override fun getPositiveButtonText() = R.string.ok

    @Composable
    override fun Content() = TimetableConfigContent(activity.applicationContext as App)

    override fun onDismiss() {
        if (reloadOnDismiss && activity is MainActivity) activity.reloadTarget()
        activity.sendBroadcast(Intent(TimetableFragment.ACTION_RELOAD_PAGES))
    }
}

@Composable
private fun TimetableConfigContent(app: App) {
    val cfg = app.profile.config.ui
    val hasAttendance = app.profile.loginStoreType.features.contains(FeatureType.ATTENDANCE)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionHeader(R.string.config_appearance)
        var showEvents by remember { mutableStateOf(cfg.timetableShowEvents) }
        CheckboxRow(R.string.timetable_config_show_events, showEvents) { showEvents = it; cfg.timetableShowEvents = it }
        if (hasAttendance) {
            var showAttendance by remember { mutableStateOf(cfg.timetableShowAttendance) }
            CheckboxRow(R.string.timetable_config_show_attendance, showAttendance) { showAttendance = it; cfg.timetableShowAttendance = it }
        }
        var colorSubject by remember { mutableStateOf(cfg.timetableColorSubjectName) }
        CheckboxRow(R.string.timetable_config_color_subject_name, colorSubject) { colorSubject = it; cfg.timetableColorSubjectName = it }
        var trimHours by remember { mutableStateOf(cfg.timetableTrimHourRange) }
        CheckboxRow(R.string.timetable_config_trim_hour_range, trimHours) { trimHours = it; cfg.timetableTrimHourRange = it }
    }
}
