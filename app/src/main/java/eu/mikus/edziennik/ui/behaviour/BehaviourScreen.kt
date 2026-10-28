/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.behaviour

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.NoticeFull
import eu.mikus.edziennik.ui.compose.IconicsIcon

@Composable
fun BehaviourScreen(
    state: BehaviourUiState,
    onFilterChange: (SemesterFilter) -> Unit,
    onMarkSeen: (NoticeFull) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    when (state) {
        BehaviourUiState.Loading ->
            Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        is BehaviourUiState.Content ->
            Column(modifier.fillMaxSize()) {
                BehaviourSummaryHeader(state.summary, state.filter, onFilterChange)
                if (state.notices.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.notices_no_data),
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.notices, key = { it.id }) { notice ->
                            NoticeRow(notice)
                            // Mark-seen on appearance: fires once per id (keyed), guarded on !seen.
                            LaunchedEffect(notice.id) {
                                if (!notice.seen) onMarkSeen(notice)
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun BehaviourSummaryHeader(
    summary: BehaviourSummary,
    filter: SemesterFilter,
    onFilterChange: (SemesterFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Box {
            val title = when (filter) {
                SemesterFilter.YEAR -> stringResource(R.string.notices_summary_title_year)
                else -> stringResource(R.string.notices_summary_title_semester_format, filter.semester ?: 0)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable { expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.summary_mode_year)) },
                    onClick = { onFilterChange(SemesterFilter.YEAR); expanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.summary_mode_semester_1)) },
                    onClick = { onFilterChange(SemesterFilter.SEMESTER_1); expanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.summary_mode_semester_2)) },
                    onClick = { onFilterChange(SemesterFilter.SEMESTER_2); expanded = false },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCount(stringResource(R.string.notices_praises_title), summary.praises, MaterialTheme.colorScheme.onSurface)
            SummaryCount(
                stringResource(R.string.notices_warnings_title),
                summary.warnings,
                if (summary.warnings >= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            SummaryCount(stringResource(R.string.notices_other_title), summary.other, MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryCount(label: String, count: Int, countColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = countColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NoticeRow(notice: NoticeFull) {
    val containerColor =
        if (!notice.seen) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = noticeTypeStyle(notice.type)
            IconicsIcon(style.icon, contentDescription = null, sizeDp = 36, tint = colorResource(style.color))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    notice.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notice.teacherName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val context = LocalContext.current
                    // Keyed on addedDate, the only field of the notice that the text reads, and on
                    // context, from which the unknown label is resolved.
                    val date = remember(notice.addedDate, context) { noticeDateText(notice, context) }
                    Text(
                        date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
