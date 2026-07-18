/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Shared config-dialog controls (M3), used by all the ui/dialogs/settings ComposeDialogs. */
@Composable
internal fun CheckboxRow(
    labelRes: Int,
    checked: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SectionHeader(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
internal fun RadioRow(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onSelect: () -> Unit,
) {
    Row(
        modifier = modifier.clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun RadioRow(
    labelRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onSelect: () -> Unit,
) = RadioRow(stringResource(labelRes), selected, modifier, onSelect)
