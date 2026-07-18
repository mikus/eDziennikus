/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-20.
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.utils.models.Time

class QuietHoursConfigDialog(
    activity: AppCompatActivity,
    private val onChangeListener: (() -> Unit)? = null,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "QuietHoursConfigDialog"
    override fun getTitleRes() = R.string.settings_sync_quiet_hours_dialog_title
    override fun getNegativeButtonText() = R.string.cancel

    @Composable
    override fun Content() {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(R.string.settings_sync_quiet_hours_set_beginning) { launchTimePicker(isStart = true) }
            Row(R.string.settings_sync_quiet_hours_set_end) { launchTimePicker(isStart = false) }
        }
    }

    @Composable
    private fun Row(labelRes: Int, onClick: () -> Unit) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable { dialog.dismiss(); onClick() }.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }

    private fun launchTimePicker(isStart: Boolean) {
        val time = (if (isStart) app.config.sync.quietHoursStart else app.config.sync.quietHoursEnd) ?: return
        val picker = MaterialTimePicker.Builder()
            .setTitleText(if (isStart) R.string.settings_sync_quiet_hours_set_beginning else R.string.settings_sync_quiet_hours_set_end)
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(time.hour)
            .setMinute(time.minute)
            .build()
        picker.show(activity.supportFragmentManager, TAG)
        picker.addOnPositiveButtonClickListener {
            app.config.sync.quietHoursEnabled = true
            if (isStart) app.config.sync.quietHoursStart = Time(picker.hour, picker.minute, 0)
            else app.config.sync.quietHoursEnd = Time(picker.hour, picker.minute, 0)
            onChangeListener?.invoke()
        }
    }
}
