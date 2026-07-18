/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-20.
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ext.HOUR
import eu.mikus.edziennik.ext.MINUTE
import eu.mikus.edziennik.ext.getSyncInterval
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class SyncIntervalDialog(
    activity: AppCompatActivity,
    private val onChangeListener: (() -> Unit)? = null,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "SyncIntervalDialog"
    override fun getTitleRes() = R.string.settings_sync_sync_interval_dialog_title
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    private val intervals = listOf(
        30 * MINUTE, 45 * MINUTE, 60 * MINUTE, 90 * MINUTE,
        2 * HOUR, 3 * HOUR, 4 * HOUR, 6 * HOUR, 10 * HOUR,
    ).map { it.toInt() }

    private var selectedInterval by mutableIntStateOf((activity.applicationContext as App).config.sync.interval)

    @Composable
    override fun Content() {
        val context = LocalContext.current
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.settings_sync_sync_interval_dialog_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            intervals.forEach { sec ->
                RadioRow(context.getSyncInterval(sec), selectedInterval == sec) { selectedInterval = sec }
            }
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        app.config.sync.interval = selectedInterval
        onChangeListener?.invoke()
        return DISMISS
    }
}
