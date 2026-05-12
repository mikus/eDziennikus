/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-20
 */

package eu.mikus.edziennik.ui.dialogs

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.DialogBellSyncBinding
import eu.mikus.edziennik.ext.resolveDrawable
import eu.mikus.edziennik.ext.startCoroutineTimer
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.utils.models.Time

class BellSyncDialog(
    activity: AppCompatActivity,
    private val bellTime: Time,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<DialogBellSyncBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "BellSyncDialog"

    override fun getTitleRes() = R.string.bell_sync_title
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogBellSyncBinding.inflate(layoutInflater)

    override fun getNeutralButtonText() = R.string.cancel

    private var counterJob: Job? = null

    private val actualBellDiff: Pair<Time, Int>
        get() {
            val now = Time.getNow()
            val bellDiff = Time.diff(now, bellTime)
            val multiplier = if (bellTime > now) -1 else 1
            return Pair(bellDiff, multiplier)
        }

    override suspend fun onShow() {
        b.bellSyncButton.setOnClickListener {
            val (bellDiff, multiplier) = actualBellDiff
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

        if (Time.diff(Time.getNow(), bellTime) > Time(2, 0, 0)) { // Easter egg ^^
            b.bellSyncButton.setImageDrawable(R.drawable.ic_bell_wtf.resolveDrawable(app)) // wtf
        }

        counterJob = startCoroutineTimer(repeatMillis = 500) {
            val (bellDiff, multiplier) = actualBellDiff
            val bellDiffText = (if (multiplier == -1) '-' else '+') + bellDiff.stringHMS
            b.bellSyncHowto.text =
                app.getString(R.string.bell_sync_howto, bellTime.stringHM, bellDiffText)
        }
    }

    override fun onDismiss() {
        counterJob?.cancel()
    }
}
