/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.IconicsIcon

private val AboutBlue = Color(0xFF1976D2)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggle: (SettingsToggle, Boolean) -> Unit,
    onAction: (SettingsAction) -> Unit,
    profileAvatar: Drawable?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.cards, key = { it.titleRes ?: it.style.name }) { card ->
            SettingsCardView(card, onToggle, onAction, profileAvatar)
        }
    }
}

@Composable
private fun SettingsCardView(
    card: SettingsCardUi,
    onToggle: (SettingsToggle, Boolean) -> Unit,
    onAction: (SettingsAction) -> Unit,
    profileAvatar: Drawable?,
) {
    val about = card.style == CardStyle.AboutBlueDark
    val content = if (about) Color.White else MaterialTheme.colorScheme.onSurface
    val colors = if (about) CardDefaults.cardColors(containerColor = AboutBlue, contentColor = Color.White)
    else CardDefaults.cardColors()

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), colors = colors) {
        Column(Modifier.padding(vertical = 8.dp)) {
            card.titleRes?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            card.items.forEach { item -> SettingsItemView(item, content, onToggle, onAction, profileAvatar) }
        }
    }
}

@Composable
private fun SettingsItemView(
    item: SettingsItem,
    content: Color,
    onToggle: (SettingsToggle, Boolean) -> Unit,
    onAction: (SettingsAction) -> Unit,
    profileAvatar: Drawable?,
) {
    when (item) {
        is SettingsItem.Title -> TitleRow(item, content)
        is SettingsItem.Section -> SectionRow(item, content)
        is SettingsItem.Profile -> ProfileRow(item, profileAvatar, content) { onAction(SettingsAction.EditProfile) }
        is SettingsItem.Action ->
            if (item.action == SettingsAction.VersionTap) VersionRow(item, content, onAction)
            else ActionRow(item.textRes, item.subTextRes, item.subText, item.icon, content) { onAction(item.action) }
        is SettingsItem.Switch -> SwitchRow(item, content, onToggle)
        is SettingsItem.ActionSwitch -> ActionSwitchRow(item, content, onToggle, onAction)
        is SettingsItem.More -> MoreGroup(item, content, onToggle, onAction, profileAvatar)
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    content: Color,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Box { icon() }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = content)
            if (subtitle != null) Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let { Spacer(Modifier.width(16.dp)); it() }
    }
}

@Composable
private fun TitleRow(item: SettingsItem.Title, content: Color) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(item.iconRes), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(item.titleRes), style = MaterialTheme.typography.titleLarge, color = content)
            Text(stringResource(item.subTextRes), style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SectionRow(item: SettingsItem.Section, content: Color) {
    Text(
        stringResource(item.textRes),
        style = MaterialTheme.typography.labelMedium,
        color = content.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ProfileRow(item: SettingsItem.Profile, avatar: Drawable?, content: Color, onClick: () -> Unit) {
    val painter = remember(avatar) {
        avatar?.let { BitmapPainter(it.toBitmap(width = 144, height = 144).asImageBitmap()) }
    }
    SettingsRow(
        icon = {
            if (painter != null) Image(painter, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape))
            else IconicsIcon(CommunityMaterial.Icon.cmd_account_circle, contentDescription = null, sizeDp = 48, tint = content)
        },
        title = item.name, subtitle = item.subname, content = content, onClick = onClick,
    )
}

@Composable
private fun ActionRow(
    textRes: Int, subTextRes: Int?, subText: String?,
    icon: com.mikepenz.iconics.typeface.IIcon, content: Color, onClick: () -> Unit,
) {
    val subtitle = subText ?: subTextRes?.let { stringResource(it) }
    SettingsRow(
        icon = { IconicsIcon(icon, contentDescription = null, tint = content) },
        title = stringResource(textRes), subtitle = subtitle, content = content, onClick = onClick,
    )
}

@Composable
private fun SwitchRow(item: SettingsItem.Switch, content: Color, onToggle: (SettingsToggle, Boolean) -> Unit) {
    SettingsRow(
        icon = { IconicsIcon(item.icon, contentDescription = null, tint = content) },
        title = stringResource(item.textRes),
        subtitle = item.subTextRes?.let { stringResource(it) },
        content = content,
        onClick = { onToggle(item.toggle, !item.checked) },
        trailing = { Switch(checked = item.checked, onCheckedChange = { onToggle(item.toggle, it) }) },
    )
}

@Composable
private fun ActionSwitchRow(
    item: SettingsItem.ActionSwitch, content: Color,
    onToggle: (SettingsToggle, Boolean) -> Unit, onAction: (SettingsAction) -> Unit,
) {
    val subtitle = if (item.checked) item.subTextChecked else stringResource(item.subTextDisabledRes)
    SettingsRow(
        icon = { IconicsIcon(item.icon, contentDescription = null, tint = content) },
        title = stringResource(item.textRes),
        subtitle = subtitle,
        content = content,
        onClick = { onAction(item.action) },
        trailing = { Switch(checked = item.checked, onCheckedChange = { onToggle(item.toggle, it) }) },
    )
}

@Composable
private fun MoreGroup(
    item: SettingsItem.More, content: Color,
    onToggle: (SettingsToggle, Boolean) -> Unit, onAction: (SettingsAction) -> Unit,
    profileAvatar: Drawable?,
) {
    var expanded by remember { mutableStateOf(false) }
    if (!expanded) {
        SettingsRow(
            icon = { IconicsIcon(CommunityMaterial.Icon.cmd_chevron_down, contentDescription = null, tint = content) },
            title = stringResource(R.string.settings_more_text), subtitle = null, content = content,
            onClick = { expanded = true },
        )
    } else {
        item.items.forEach { SettingsItemView(it, content, onToggle, onAction, profileAvatar) }
    }
}

@Composable
private fun VersionRow(item: SettingsItem.Action, content: Color, onAction: (SettingsAction) -> Unit) {
    var count by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val version = item.subText ?: ""
    Column {
        SettingsRow(
            icon = { IconicsIcon(item.icon, contentDescription = null, tint = content) },
            title = stringResource(item.textRes),
            subtitle = if (revealed) "$version 💣" else version,
            content = content,
            onClick = {
                revealed = true
                count++
                if (count < 7) {
                    onAction(SettingsAction.VersionTap)         // host: 😂 toast
                } else {
                    onAction(SettingsAction.VersionEasterEgg)   // host: play ogarnij_sie
                    count = 0
                }
            },
        )
        if (revealed) ActionRow(
            textRes = R.string.settings_about_version_details_text,
            subTextRes = R.string.settings_about_version_details_subtext,
            subText = null, icon = CommunityMaterial.Icon.cmd_cellphone_information, content = content,
        ) { onAction(SettingsAction.VersionDetails) }
    }
}
