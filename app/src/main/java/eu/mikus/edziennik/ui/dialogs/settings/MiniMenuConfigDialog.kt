/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-18.
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
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.base.enums.NavTargetLocation
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class MiniMenuConfigDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "MiniMenuConfigDialog"
    override fun getTitleRes() = R.string.settings_theme_mini_drawer_buttons_dialog_title
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    private val eligible = NavTarget.values().filter {
        (!it.devModeOnly || App.devMode) && it.location in listOf(
            NavTargetLocation.DRAWER,
            NavTargetLocation.DRAWER_BOTTOM,
        )
    }

    private val checked = mutableStateMapOf<NavTarget, Boolean>().apply {
        val current = (activity.applicationContext as App).config.ui.miniMenuButtons
        eligible.forEach { put(it, it in current) }
    }

    @Composable
    override fun Content() {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.settings_theme_mini_drawer_buttons_dialog_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            eligible.forEach { target ->
                CheckboxRow(target.nameRes, checked[target] == true) { checked[target] = it }
            }
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        app.config.ui.miniMenuButtons = checked.filterValues { it }.keys
        if (activity is MainActivity) {
            activity.setDrawerItems()
            activity.drawer.updateBadges()
        }
        return DISMISS
    }
}
