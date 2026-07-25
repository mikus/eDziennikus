/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
fun LoginSyncErrorScreen(errorDetail: String?, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        IconicsIcon(CommunityMaterial.Icon.cmd_alert_circle_outline, null, sizeDp = 32, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 32.dp))
        Text(stringResource(R.string.login_sync_error_title), fontSize = 24.sp, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.login_sync_error_subtitle), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        errorDetail?.let { Text(it, modifier = Modifier.padding(top = 16.dp)) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.padding(vertical = 8.dp).align(Alignment.End)) {
            Text(stringResource(R.string.next))
        }
    }
}
