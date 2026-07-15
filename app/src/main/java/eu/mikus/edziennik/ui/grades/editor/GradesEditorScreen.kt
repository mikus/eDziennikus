/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.R
import java.text.DecimalFormat

/**
 * Stateless what-if Grades editor. Mirrors the legacy [GradesEditorFragment] flow, but the
 * name/weight PopupMenus are Compose [DropdownMenu]s.
 *
 * @param gradeColor resolves a grade-name pill color (host-bound Colors.gradeNameToColor edge seam)
 * @param onEditName called with a picked grade option (name+value) for an existing row
 * @param onEditWeight called with a new weight for an existing row
 * @param onRemove called with a row id to remove it (swipe)
 * @param onAdd called with the picked (name+value) then weight for a new row
 * @param onRestore reloads the original grade set
 * @param onCustomWeight host shows the MaterialAlertDialog free-text weight input, then calls back
 */
@Composable
fun GradesEditorScreen(
    state: GradesEditorUiState,
    gradeColor: (String) -> Color,
    onEditName: (id: Long, option: EditorGradeOption) -> Unit,
    onEditWeight: (id: Long, weight: Float) -> Unit,
    onRemove: (id: Long) -> Unit,
    onAdd: (option: EditorGradeOption, weight: Float) -> Unit,
    onRestore: () -> Unit,
    onCustomWeight: (onPicked: (Float) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state as? GradesEditorUiState.Content ?: return
    Column(modifier.fillMaxSize()) {
        Text(
            content.subjectName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            stringResource(R.string.grades_semester_header_format, content.semester),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AverageCard(content, gradeColor)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AddGradeButton(
                modifier = Modifier.weight(1f),
                onAdd = onAdd,
                onCustomWeight = onCustomWeight,
            )
            OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.grades_editor_restore))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(content.grades, key = { it.id }) { g ->
                val dismiss = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismiss.settledValue) {
                    if (dismiss.settledValue != SwipeToDismissBoxValue.Settled) onRemove(g.id)
                }
                SwipeToDismissBox(state = dismiss, backgroundContent = {}) {
                    EditorRow(g, gradeColor, onEditName, onEditWeight, onCustomWeight)
                }
            }
        }
    }
}

@Composable
private fun AverageCard(content: GradesEditorUiState.Content, gradeColor: (String) -> Color) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AverageRow(R.string.grades_editor_semester_average, content.averageBefore, content.averageAfter, gradeColor)
            if (content.yearAverageVisible) {
                AverageRow(R.string.grades_editor_year_average, content.yearAverageBefore, content.yearAverageAfter, gradeColor)
            }
        }
    }
}

@Composable
private fun AverageRow(labelRes: Int, before: Float, after: Float, gradeColor: (String) -> Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        AveragePill(before, gradeColor)
        Spacer(Modifier.width(8.dp))
        Text("→")
        Spacer(Modifier.width(8.dp))
        AveragePill(after, gradeColor)
    }
}

@Composable
private fun AveragePill(avg: Float, gradeColor: (String) -> Color) {
    val color = gradeColor(GradesEditorCalculator.colorGradeInt(avg).toString())
    val textColor = if (color.luminance() > 0.25f) Color(0xAA000000) else Color(0xCCFFFFFF)
    Text(
        "%.02f".format(avg),
        color = textColor,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun EditorRow(
    g: EditorGrade,
    gradeColor: (String) -> Color,
    onEditName: (Long, EditorGradeOption) -> Unit,
    onEditWeight: (Long, Float) -> Unit,
    onCustomWeight: (onPicked: (Float) -> Unit) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NamePillMenu(g.name, gradeColor) { option -> onEditName(g.id, option) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.category, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                WeightText(
                    g = g,
                    onEditWeight = { onEditWeight(g.id, it) },
                    onCustomWeight = onCustomWeight,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.grades_value_format, "%.02f".format(g.value)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Grade-name pill; tapping opens the 19-entry name [DropdownMenu]. */
@Composable
private fun NamePillMenu(
    name: String,
    gradeColor: (String) -> Color,
    onPicked: (EditorGradeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val color = gradeColor(name)
    val textColor = if (color.luminance() > 0.25f) Color(0xAA000000) else Color(0xCCFFFFFF)
    Box {
        Text(
            text = name,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color, RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        GradeOptionsMenu(expanded = expanded, onDismiss = { expanded = false }) { option ->
            expanded = false
            onPicked(option)
        }
    }
}

/** The weight label for a row; tapping opens the weight [DropdownMenu]. */
@Composable
private fun WeightText(
    g: EditorGrade,
    onEditWeight: (Float) -> Unit,
    onCustomWeight: (onPicked: (Float) -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (g.weight == 0f) {
        stringResource(R.string.grades_weight_not_counted)
    } else {
        stringResource(R.string.grades_weight_format, DecimalFormat("0.##").format(g.weight.toDouble()))
    }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { expanded = true },
        )
        WeightMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onWeightPicked = { expanded = false; onEditWeight(it) },
            onCustomClick = { expanded = false; onCustomWeight { w -> onEditWeight(w) } },
        )
    }
}

/** "Add grade" button: name-pick → weight-pick → [onAdd]. */
@Composable
private fun AddGradeButton(
    modifier: Modifier = Modifier,
    onAdd: (EditorGradeOption, Float) -> Unit,
    onCustomWeight: (onPicked: (Float) -> Unit) -> Unit,
) {
    var nameExpanded by remember { mutableStateOf(false) }
    var weightExpanded by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<EditorGradeOption?>(null) }
    Box(modifier) {
        Button(onClick = { nameExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.grades_editor_add_grade))
        }
        GradeOptionsMenu(expanded = nameExpanded, onDismiss = { nameExpanded = false }) { option ->
            nameExpanded = false
            picked = option
            weightExpanded = true
        }
        WeightMenu(
            expanded = weightExpanded,
            onDismiss = { weightExpanded = false },
            onWeightPicked = { w -> weightExpanded = false; picked?.let { onAdd(it, w) } },
            onCustomClick = { weightExpanded = false; onCustomWeight { w -> picked?.let { onAdd(it, w) } } },
        )
    }
}

/** DropdownMenu over the 19-entry [GradesEditorCalculator.GRADE_OPTIONS] catalog. */
@Composable
private fun GradeOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPicked: (EditorGradeOption) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        GradesEditorCalculator.GRADE_OPTIONS.forEach { option ->
            DropdownMenuItem(text = { Text(option.name) }, onClick = { onPicked(option) })
        }
    }
}

/** DropdownMenu of weights 0..6 plus a "custom" entry. */
@Composable
private fun WeightMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onWeightPicked: (Float) -> Unit,
    onCustomClick: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (i in 0..6) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.grades_editor_weight_format, i.toString())) },
                onClick = { onWeightPicked(i.toFloat()) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.grades_editor_weight_other)) },
            onClick = onCustomClick,
        )
    }
}
