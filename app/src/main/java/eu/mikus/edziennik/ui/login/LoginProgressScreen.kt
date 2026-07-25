/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.IconicsIcon

@Composable
fun LoginProgressScreen() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        IconicsIcon(CommunityMaterial.Icon2.cmd_login, null, sizeDp = 32, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 32.dp))
        Text(stringResource(R.string.login_progress_title), fontSize = 24.sp, modifier = Modifier.padding(top = 16.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
}
