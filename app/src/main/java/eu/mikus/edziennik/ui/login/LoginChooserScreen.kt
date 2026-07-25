/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.IconicsIcon

@Composable
fun LoginChooserScreen(
    versionText: String,
    cancelVisible: Boolean,
    onModeClick: (LoginInfo.Register, LoginInfo.Mode) -> Unit,
    onVersionClick: () -> Unit,
    onHelpClick: () -> Unit,
    onCancel: () -> Unit,
) {
    // Expanded register set, hoisted (replaces LoginInfo.chooserList + ExpandableItemModel.state).
    val expanded = remember { mutableStateMapOf<LoginInfo.Register, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        IconicsIcon(
            icon = CommunityMaterial.Icon3.cmd_school_outline,
            contentDescription = null,
            sizeDp = 32,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp),
        )
        Text(
            text = stringResource(R.string.login_chooser_title),
            fontSize = 24.sp,
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.login_chooser_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 2.dp),
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
            LoginInfo.list.forEach { register ->
                val visibleModes = register.loginModes.filter { App.devMode || !it.isDevOnly }
                item(key = register.loginType) {
                    RegisterRow(register) {
                        if (visibleModes.size == 1) onModeClick(register, register.loginModes.first())
                        else expanded[register] = !(expanded[register] ?: false)
                    }
                }
                if (expanded[register] == true) {
                    items(visibleModes, key = { it.loginMode }) { mode ->
                        ModeRow(mode) { onModeClick(register, mode) }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.login_copyright_notice),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cancelVisible) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            }
            Text(
                text = versionText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f).clickable(onClick = onVersionClick).padding(vertical = 8.dp),
            )
            OutlinedButton(onClick = onHelpClick, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.help))
            }
        }
    }
}

@Composable
private fun RegisterRow(register: LoginInfo.Register, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(register.registerLogo),
            contentDescription = null,
            modifier = Modifier.width(100.dp).height(60.dp),
        )
        VerticalDivider8()
        Text(stringResource(register.registerName), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ModeRow(mode: LoginInfo.Mode, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp).clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(mode.icon),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        VerticalDivider8()
        Column {
            ModeBadge(mode)
            Text(stringResource(mode.name), style = MaterialTheme.typography.titleMedium)
            mode.hintText?.let {
                Text(stringResource(it), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ModeBadge(mode: LoginInfo.Mode) {
    val badge: Pair<Int, Int>? = when {
        mode.isDevOnly -> R.string.login_chooser_mode_dev_only to R.color.md_red_300
        mode.isTesting -> R.string.login_chooser_mode_testing to R.color.md_yellow_300
        mode.isRecommended -> R.string.login_chooser_mode_recommended to R.color.md_blue_300
        else -> null
    }
    badge?.let { (labelRes, colorRes) ->
        Row(
            Modifier.background(colorResource(colorRes), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconicsIcon(
                icon = CommunityMaterial.Icon.cmd_alert_circle_outline,
                contentDescription = null,
                sizeDp = 12,
                tint = colorResource(R.color.md_black_1000),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(labelRes),
                color = colorResource(R.color.md_black_1000),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun VerticalDivider8() {
    Spacer(Modifier.width(8.dp))
    VerticalDivider(
        modifier = Modifier.height(40.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Spacer(Modifier.width(8.dp))
}
