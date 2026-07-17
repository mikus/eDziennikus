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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class AttendanceConfigDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "AttendanceConfigDialog"
    override fun getTitleRes() = R.string.menu_attendance_config
    override fun getPositiveButtonText() = R.string.ok

    @Composable
    override fun Content() = AttendanceConfigContent(activity.applicationContext as App)

    override fun onDismiss() {
        if (reloadOnDismiss && activity is MainActivity) activity.reloadTarget()
    }
}

@Composable
private fun AttendanceConfigContent(app: App) {
    val cfg = app.profile.config.attendance
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionHeader(R.string.attendance_config_title)
        var useSymbols by remember { mutableStateOf(cfg.useSymbols) }
        CheckboxRow(R.string.attendance_config_use_symbols, useSymbols) { useSymbols = it; cfg.useSymbols = it }
        Text(
            stringResource(R.string.attendance_config_use_symbols_hint),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
        )
        var group by remember { mutableStateOf(cfg.groupConsecutiveDays) }
        CheckboxRow(R.string.attendance_config_group_consecutive_days, group) { group = it; cfg.groupConsecutiveDays = it }
        var showPresence by remember { mutableStateOf(cfg.showPresenceInMonth) }
        CheckboxRow(R.string.attendance_config_show_presence_in_month, showPresence) { showPresence = it; cfg.showPresenceInMonth = it }
    }
}
