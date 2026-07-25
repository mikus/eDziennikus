/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.IconicsIcon

@Composable
fun LoginSummaryScreen(
    profiles: List<LoginViewModel.LoginSummaryItem>,
    onToggle: (Int) -> Unit,
    onAddStudent: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            IconicsIcon(CommunityMaterial.Icon.cmd_account_check_outline, null, sizeDp = 32, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 32.dp))
            Text(stringResource(R.string.login_summary_title), fontSize = 24.sp, modifier = Modifier.padding(top = 16.dp))
            Text(stringResource(R.string.login_summary_subtitle), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            items(profiles, key = { it.profile.id }) { item -> SummaryRow(item) { onToggle(item.profile.id) } }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAddStudent, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.login_summary_add_student))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone, enabled = profiles.any { it.isSelected }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun SummaryRow(item: LoginViewModel.LoginSummaryItem, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.isSelected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(start = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.profile.name ?: "", fontSize = 16.sp, modifier = Modifier.weight(1f, fill = false))
                Text(
                    stringResource(if (item.profile.isParent) R.string.account_type_parent else R.string.account_type_child),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            item.profile.subname?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
        DrawableImage(resId = item.modeIcon, contentDescription = null, modifier = Modifier.size(24.dp).padding(start = 16.dp))
    }
}
