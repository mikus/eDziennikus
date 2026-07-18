/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-19.
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class AppLanguageDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "AppLanguageDialog"
    override fun getTitleRes() = R.string.app_language_dialog_title
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    private val languages = listOf(
        R.string.language_system to "",
        R.string.language_polish to "pl",
        R.string.language_english to "en",
        R.string.language_german to "de",
    )

    private var selectedLang by mutableStateOf((activity.applicationContext as App).config.ui.language ?: "")

    @Composable
    override fun Content() {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.app_language_dialog_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            languages.forEach { (labelRes, code) ->
                RadioRow(labelRes, selectedLang == code) { selectedLang = code }
            }
        }
    }

    override suspend fun onPositiveClick(): Boolean {
        val newLang = selectedLang.ifEmpty { null }
        if (app.config.ui.language == newLang) return DISMISS  // no-op guard (mirrors ThemeChooser)
        app.config.ui.language = newLang
        activity.recreate()
        return NO_DISMISS
    }
}
