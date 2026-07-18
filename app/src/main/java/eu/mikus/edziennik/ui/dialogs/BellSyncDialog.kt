/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-20
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.settings.bellSyncActualDiff
import eu.mikus.edziennik.utils.models.Time
import kotlinx.coroutines.delay

class BellSyncDialog(
    activity: AppCompatActivity,
    private val bellTime: Time,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "BellSyncDialog"
    override fun getTitleRes() = R.string.bell_sync_title
    override fun getNeutralButtonText() = R.string.cancel

    @Composable
    override fun Content() {
        var now by remember { mutableStateOf(Time.getNow()) }
        LaunchedEffect(Unit) {
            while (true) { delay(500); now = Time.getNow() }
        }
        val (bellDiff, multiplier) = bellSyncActualDiff(now, bellTime)
        val bellDiffText = (if (multiplier == -1) '-' else '+') + bellDiff.stringHMS
        val wtf = Time.diff(Time.getNow(), bellTime) > Time(2, 0, 0)

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.bell_sync_howto, bellTime.stringHM, bellDiffText),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Image(
                painter = painterResource(if (wtf) R.drawable.ic_bell_wtf else R.drawable.ic_bell),
                contentDescription = null,
                modifier = Modifier.size(96.dp).clickable { confirmSync() },
            )
        }
    }

    private fun confirmSync() {
        // Recompute fresh at tap-time (not from the ≤500 ms-stale composition values) — matches legacy.
        val (bellDiff, multiplier) = bellSyncActualDiff(Time.getNow(), bellTime)
        val bellDiffText = (if (multiplier == -1) '-' else '+') + bellDiff.stringHMS
        app.config.timetable.bellSyncDiff = bellDiff
        app.config.timetable.bellSyncMultiplier = multiplier
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.bell_sync_title)
            .setMessage(app.getString(R.string.bell_sync_results, bellDiffText))
            .setPositiveButton(R.string.ok) { resultsDialog, _ ->
                resultsDialog.dismiss()
                dialog.dismiss()
                if (activity is MainActivity) activity.reloadTarget()
            }
            .show()
    }
}
