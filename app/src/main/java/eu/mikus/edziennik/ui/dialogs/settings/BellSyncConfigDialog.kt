/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-20.
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class BellSyncConfigDialog(
    activity: AppCompatActivity,
    private val onChangeListener: (() -> Unit)? = null,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "BellSyncConfigDialog"
    override fun getTitleRes() = R.string.bell_sync_title
    override fun getPositiveButtonText() = R.string.ok
    override fun getNeutralButtonText() = R.string.reset
    override fun getNegativeButtonText() = R.string.cancel

    private val cfg = (activity.applicationContext as App).config.timetable
    private var text by mutableStateOf(
        cfg.bellSyncDiff?.let { (if (cfg.bellSyncMultiplier == -1) "-" else "+") + it.stringHMS } ?: "+0:00:00",
    )

    @Composable
    override fun Content() {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.bell_sync_adjust_content),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val invalid = text.isNotEmpty() && bellSyncParse(text) == null
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = invalid,
                placeholder = { Text("±H:MM:SS") },
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.bell_sync_adjust_error)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        val parsed = bellSyncParse(text)
        if (parsed == null) {
            Toast.makeText(activity, R.string.bell_sync_adjust_error, Toast.LENGTH_SHORT).show()
            return NO_DISMISS
        }
        val (time, multiplier) = parsed
        app.config.timetable.bellSyncDiff = if (time.value == 0) null else time
        app.config.timetable.bellSyncMultiplier = multiplier
        onChangeListener?.invoke()
        return DISMISS
    }

    override suspend fun onNeutralClick(): Boolean {
        app.config.timetable.bellSyncDiff = null
        app.config.timetable.bellSyncMultiplier = 0
        onChangeListener?.invoke()
        return DISMISS
    }
}
