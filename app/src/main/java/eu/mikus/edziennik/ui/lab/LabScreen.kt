/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.mikus.edziennik.ui.dialogs.base.FormDropdown
import eu.mikus.edziennik.ui.dialogs.base.FormDropdownItem

/**
 * One scrolling screen (design D2): the control panel, then the six-root inspector. The tab strip it
 * replaces was already commented out at `lab_fragment.xml@53a07964:28-36`, and its two titles were hardcoded
 * English literals (`LabFragment.kt@53a07964:52-53`).
 */
@Composable
fun LabScreen(
    panel: LabPanelUiState,
    tree: LabTreeUiState,
    onToggle: (LabToggle, Boolean) -> Unit,
    onAction: (LabAction) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onNodeClick: (LabNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        items(panel.controls, key = ::controlKey) { control ->
            LabControlView(control, onToggle, onAction, onProfileSelected)
        }
        item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
        items(tree.rows, key = { it.path.stableId }) { node ->
            when (node) {
                is LabNode.Container -> LabRow(node) { onNodeClick(node) }
                is LabNode.Leaf -> LabLeafRow(node) { onNodeClick(node) }
            }
        }
    }
}

private fun controlKey(control: LabControl): String = when (control) {
    is LabControl.Button -> "b:${control.action.name}"
    is LabControl.Check -> "c:${control.toggle.name}"
    is LabControl.ProfilePicker -> "p"
    is LabControl.Cookies -> "k"
}

@Composable
private fun LabControlView(
    control: LabControl,
    onToggle: (LabToggle, Boolean) -> Unit,
    onAction: (LabAction) -> Unit,
    onProfileSelected: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            // The 24 dp `layout_marginTop` groupings of the old LinearLayout, decided in the builder.
            .padding(top = if (control.gapBefore) 24.dp else 0.dp),
    ) {
        when (control) {
            is LabControl.Button -> LabButton(control, onAction)
            // The old MaterialCheckBox was match_parent with android:text, so the whole row toggled
            // and the text was the box's own label. Hoisting the toggle onto the Row restores both the
            // affordance and the accessibility node - SettingsScreen's switch rows hoist the tap onto
            // the row the same way (`SettingsScreen.kt:129-131`), though with `clickable` and a live
            // Switch, so they keep the two separate nodes this one merges. The repo's three checkbox
            // rows (`ConfigControls.kt:28-33`, `MessagesComposeScreen.kt:587-596`,
            // `GradesConfigDialog.kt:250-255`) all keep a live `Checkbox` too and so also read as two
            // nodes; merging here is deliberate, not an oversight, and they are left alone.
            is LabControl.Check -> Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = control.checked,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(control.toggle, it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = control.checked, onCheckedChange = null)
                Text(control.label)
            }
            is LabControl.ProfilePicker -> FormDropdown(
                hint = "Profile",
                items = control.profiles.map {
                    FormDropdownItem(it.id.toLong(), "${it.id} ${it.name} archived ${it.archived}")
                },
                selectedId = control.selectedId.toLong(),
                onSelect = { onProfileSelected(it.id.toInt()) },
            )
            is LabControl.Cookies -> CookiesReadout(control.groups)
        }
    }
}

@Composable
private fun LabButton(control: LabControl.Button, onAction: (LabAction) -> Unit) {
    val onClick = { onAction(control.action) }
    val modifier = Modifier.fillMaxWidth()
    when (control.style) {
        LabControlStyle.Filled -> Button(onClick, modifier) { Text(control.label) }
        LabControlStyle.Outlined -> OutlinedButton(onClick, modifier) { Text(control.label) }
        // `app:backgroundTint="@color/windowBackgroundRed"` (lab_fragment.xml@53a07964:165) becomes the M3
        // error role. windowBackgroundRed stays live via styles.xml:339, so it is not an orphan.
        LabControlStyle.Danger -> Button(
            onClick,
            modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text(control.label) }
    }
}

/**
 * `LabPageFragment.kt@53a07964:180-204`, cue for cue: bold domain, **underlined name when the cookie is
 * persistent**, italic secondary-coloured value, monospace throughout (`lab_fragment.xml@53a07964:122`).
 * Compose takes no `Spannable`, so `asUnderlineSpannable` is re-expressed as a [SpanStyle] here and the
 * extension, having lost its last caller, was deleted in the same phase.
 */
@Composable
private fun CookiesReadout(groups: List<CookieGroup>) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(groups, secondary) { cookiesText(groups, secondary) }
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

private fun cookiesText(groups: List<CookieGroup>, secondary: Color) = buildAnnotatedString {
    groups.forEachIndexed { index, group ->
        if (index > 0) append("\n\n")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(group.domain) }
        append(":\n")
        group.lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) append("\n")
            append("    ")
            if (line.persistent)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(line.name) }
            else
                append(line.name)
            append("=")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = secondary)) { append(line.value) }
        }
    }
}

/** `JsonObjectViewHolder` / `JsonSubObjectViewHolder` / `JsonArrayViewHolder`, selected by `style`. */
@Composable
private fun LabRow(node: LabNode.Container, onClick: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (node.depth * 8).dp + 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (node.expanded) "▾" else "▸", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            // Monospace, AppText.Medium, and the two-line clamp, as
            // lab_item_object.xml@53a07964:24-31 and lab_item_sub_object.xml@53a07964:20-27 had them.
            // The inspector is a JSON view: fixed pitch is what lines its keys up, and it is exactly
            // the kind of cheap cue a port drops without anyone noticing.
            Text(
                node.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                node.typeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = secondary,
            )
        }
        if (node.style == LabContainerStyle.Full) {
            // preview while closed, summary while open - JsonObjectViewHolder.kt@53a07964:40-41. Both are
            // non-null exactly when style == Full; orEmpty() is the compiler's price for that.
            // Only the preview was monospace (lab_item_object.xml@53a07964:58-65, its
            // android:fontFamily at :62); the summary (lab_item_object.xml@53a07964:67-73), which reads
            // "N elements" rather than JSON, declared none - so the fixed pitch follows the JSON.
            Text(
                (if (node.expanded) node.summary else node.preview).orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (node.expanded) null else FontFamily.Monospace,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `JsonElementViewHolder.kt@53a07964:31-52`: key in the secondary colour, `": "`, then the italic value. */
@Composable
private fun LabLeafRow(node: LabNode.Leaf, onClick: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(node, secondary) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = secondary)) { append(node.name) }
            append(": ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(node.displayText) }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (node.depth * 8).dp + 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // lab_item_element.xml@53a07964:19-32: monospace on both, and the key clamped to two
        // ellipsized lines - Config values are all Strings, and some are long JSON blobs.
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        node.typeLabel?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = secondary,
            )
        }
    }
}
