/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.home.HomeCardsDialog

class HomeConfigDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "HomeConfigDialog"
    override fun getTitleRes() = R.string.menu_home_config
    override fun getPositiveButtonText() = R.string.ok

    @Composable
    override fun Content() = HomeConfigContent(activity, activity.applicationContext as App)

    override fun onDismiss() {
        if (reloadOnDismiss && activity is MainActivity) activity.reloadTarget()
    }
}

@Composable
private fun HomeConfigContent(activity: AppCompatActivity, app: App) {
    val cfg = app.profile.config.ui
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionHeader(R.string.home_config_title)
        var lockCards by remember { mutableStateOf(cfg.homeCardsLocked) }
        CheckboxRow(R.string.home_config_lock_cards, lockCards) { lockCards = it; cfg.homeCardsLocked = it }
        Text(
            stringResource(R.string.home_config_lock_cards_hint),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
        )
        OutlinedButton(onClick = { HomeCardsDialog(activity, reloadOnDismiss = false).show() }) {
            Text(stringResource(R.string.home_configure_add_remove))
        }

        SectionHeader(R.string.home_events_config_title)
        SliderRow(R.string.home_config_events_limit, cfg.homeEventsLimit, 1, 20) { cfg.homeEventsLimit = it }
        SliderRow(R.string.home_config_events_weeks, cfg.homeEventsWeeks, 1, 16) { cfg.homeEventsWeeks = it }

        SectionHeader(R.string.home_grades_config_title)
        SliderRow(R.string.home_config_grades_weeks, cfg.homeGradesWeeks, 1, 16) { cfg.homeGradesWeeks = it }
    }
}

@Composable
private fun SliderRow(labelRes: Int, initial: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    var value by remember { mutableIntStateOf(initial) }
    Text("${stringResource(labelRes)}: $value", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    Slider(
        value = value.toFloat(),
        // dedupe: a drag fires onValueChange per frame, but only write config when the stepped Int changes
        onValueChange = { val v = it.toInt(); if (v != value) { value = v; onValue(v) } },
        valueRange = min.toFloat()..max.toFloat(),
        steps = max - min - 1,
        modifier = Modifier.fillMaxWidth(),
    )
}
