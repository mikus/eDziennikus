/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-20
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.settings.RadioRow
import eu.mikus.edziennik.ui.dialogs.settings.bellSyncCanSync
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BellSyncTimeChooseDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    companion object { private const val MAX_DIFF_MINUTES = 10 }

    override val TAG = "BellSyncTimeChooseDialog"
    override fun getTitleRes() = R.string.bell_sync_title
    override fun getPositiveButtonText() = R.string.ok
    override fun getNeutralButtonText() = R.string.reset
    override fun getNegativeButtonText() = R.string.cancel

    private data class Item(val label: String, val time: Time)

    private var items by mutableStateOf<List<Item>>(emptyList())
    private var selected by mutableStateOf<Item?>(null)

    @Composable
    override fun Content() {
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { if (!loaded) { loaded = true; load() } }

        val cfg = app.config.timetable
        val howto = if (cfg.bellSyncDiff != null) {
            val diffText = (if (cfg.bellSyncMultiplier == -1) '-' else '+') + cfg.bellSyncDiff!!.stringHMS
            app.getString(R.string.concat_2_strings,
                app.getString(R.string.bell_sync_choose_howto),
                app.getString(R.string.bell_sync_current_dialog, diffText))
        } else app.getString(R.string.bell_sync_choose_howto)

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(howto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
            items.forEach { item ->
                RadioRow(item.label, selected == item) { selected = item }
            }
        }
    }

    private suspend fun load() {
        val today = Date.getToday()
        val built = withContext(Dispatchers.Default) {
            val lessons = app.db.timetableDao().getAllForDateNow(App.profileId, today)
            val out = mutableListOf<Item>()
            lessons.forEach {
                if (it.type != Lesson.TYPE_NO_LESSONS && it.type != Lesson.TYPE_CANCELLED && it.type != Lesson.TYPE_SHIFTED_SOURCE) {
                    val start = it.displayStartTime ?: return@forEach
                    val end = it.displayEndTime ?: return@forEach
                    out += Item(app.getString(R.string.bell_sync_lesson_item, it.displaySubjectName, start.stringHM), start)
                    out += Item(app.getString(R.string.bell_sync_break_item, end.stringHM), end)
                }
            }
            out
        }
        if (!bellSyncCanSync(built.map { it.time }, Time.getNow(), MAX_DIFF_MINUTES)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.bell_sync_title)
                .setMessage(R.string.bell_sync_cannot_now)
                .setPositiveButton(R.string.ok, null)
                .show()
            dialog.dismiss()
            return
        }
        items = built
        val now = Time.getNow()
        selected = built.firstOrNull { it.time >= now } ?: built.lastOrNull()
    }

    override suspend fun onPositiveClick(): Boolean {
        selected?.let { BellSyncDialog(activity, it.time).show() }
        return DISMISS
    }

    override suspend fun onNeutralClick(): Boolean {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.bell_sync_title)
            .setMessage(R.string.bell_sync_reset_confirm)
            .setPositiveButton(R.string.yes) { d, _ ->
                app.config.timetable.bellSyncDiff = null
                app.config.timetable.bellSyncMultiplier = 0
                d.dismiss()
                dialog.dismiss()
                if (activity is MainActivity) activity.reloadTarget()
            }
            .setNegativeButton(R.string.no, null)
            .show()
        return NO_DISMISS
    }
}
