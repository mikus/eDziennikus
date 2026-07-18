/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-18.
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.utils.Themes

class ThemeChooserDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "ThemeChooserDialog"
    override fun getTitleRes() = R.string.settings_theme_theme_text
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    private var selectedThemeId by mutableIntStateOf(Themes.theme.id)

    @Composable
    override fun Content() {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Themes.themeList.forEach { theme ->
                RadioRow(theme.name, selectedThemeId == theme.id) { selectedThemeId = theme.id }
            }
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        if (app.config.ui.theme != selectedThemeId) {
            app.config.ui.theme = selectedThemeId
            Themes.themeInt = selectedThemeId
            activity.recreate()
        }
        return DISMISS
    }
}
