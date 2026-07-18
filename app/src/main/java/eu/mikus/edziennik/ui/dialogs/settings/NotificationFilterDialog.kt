/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-21.
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.enums.NotificationType
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class NotificationFilterDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "NotificationFilterDialog"
    override fun getTitleRes() = R.string.dialog_notification_filter_title
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    private val eligible = NotificationType.values().filter { it.enabledByDefault != null }

    // checked = ENABLED; seed = not in the persisted disabled set
    private val checked = mutableStateMapOf<NotificationType, Boolean>().apply {
        val disabled = (activity.applicationContext as App).profile.config.sync.notificationFilter
        eligible.forEach { put(it, it !in disabled) }
    }

    @Composable
    override fun Content() {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.dialog_notification_filter_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            eligible.forEach { type ->
                CheckboxRow(type.titleRes, checked[type] == true) { checked[type] = it }
            }
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        val enabled = checked.filterValues { it }.keys
        val disabled = notificationFilterDisabled(eligible, enabled)
        if (shouldWarnDisabling(disabled)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.notification_filter_warning)
                .setPositiveButton(R.string.ok) { _, _ ->
                    app.profile.config.sync.notificationFilter = disabled
                    dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return NO_DISMISS
        }
        app.profile.config.sync.notificationFilter = disabled
        return DISMISS
    }
}
