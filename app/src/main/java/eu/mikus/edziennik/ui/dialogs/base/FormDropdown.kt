/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.dialogs.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.IIcon
import eu.mikus.edziennik.ui.compose.IconicsIcon

/**
 * One selectable row for [FormDropdown]. Mirrors the legacy `TextInputDropDown.Item` fields the two
 * editors relied on: [id] (the selection key), [text] (menu + collapsed label), an optional
 * [leadingColorInt] rendered as a colored dot (the Note color picker used a tinted `cmd_circle`),
 * an optional [leadingIcon] (an Iconics [IIcon]) and an opaque [tag] the caller reads back after
 * selection (e.g. the `Note.Color` enum constant).
 */
data class FormDropdownItem(
    val id: Long,
    val text: String,
    val leadingColorInt: Int? = null,
    val leadingIcon: IIcon? = null,
    val tag: Any? = null,
)

/**
 * M3 replacement for the legacy [TextInputDropDown]: a read-only [OutlinedTextField] inside an
 * [ExposedDropdownMenuBox] that pops a menu of [items]. The current selection is derived from
 * [selectedId]; picking a row fires [onSelect] with the chosen [FormDropdownItem] (the caller reads
 * `item.tag` / `item.id`, exactly as it read `dropdown.selected` before). [isError] +
 * [supportingText] drive the inline validation surface that was `dropdown.error` in the old views.
 *
 * @OptIn is required because ExposedDropdownMenu is experimental in material3 1.4.0; `menuAnchor`
 * takes an explicit [MenuAnchorType] there (the no-arg overload is deprecated).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDropdown(
    hint: String,
    items: List<FormDropdownItem>,
    selectedId: Long?,
    onSelect: (FormDropdownItem) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = items.firstOrNull { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.text ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(hint) },
            leadingIcon = selected?.leadingSlot(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.text) },
                    leadingIcon = item.leadingSlot(),
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

/** The optional leading slot for an item: an Iconics icon takes priority, else a colored dot, else none. */
private fun FormDropdownItem.leadingSlot(): (@Composable () -> Unit)? = when {
    leadingIcon != null -> {
        { IconicsIcon(icon = leadingIcon, contentDescription = null) }
    }
    leadingColorInt != null -> {
        { ColorDot(leadingColorInt) }
    }
    else -> null
}

@Composable
private fun ColorDot(colorInt: Int) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = Color(colorInt), radius = size.minDimension / 2f)
    }
}
