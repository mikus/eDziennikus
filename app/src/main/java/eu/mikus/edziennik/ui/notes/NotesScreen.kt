/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.notes

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.ext.resolveAttr

@Composable
fun NotesScreen(
    state: NotesUiState,
    onQueryChange: (String) -> Unit,
    onNoteClick: (Note) -> Unit,
    onNoteEditClick: (Note) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    when (state) {
        NotesUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        NotesUiState.Empty ->
            Box(modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.notes_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                )
            }
        is NotesUiState.Content ->
            Column(modifier.fillMaxSize()) {
                NotesSearchField(state.query, state.resultCount, onQueryChange)
                if (state.rows.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.notes_search_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                } else {
                    val highlightColor = searchHighlightColor()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.rows, key = { rowKey(it) }) { row ->
                            when (row) {
                                is NoteRow.Header -> SectionHeader(row.ownerType)
                                is NoteRow.Item -> NoteCard(note = row.note, onNoteClick = onNoteClick, query = state.query, highlightColor = highlightColor, onNoteEditClick = onNoteEditClick)
                            }
                        }
                    }
                }
            }
    }
}

private fun rowKey(row: NoteRow): Any = when (row) {
    is NoteRow.Header -> "h_${row.ownerType.name}"
    is NoteRow.Item -> "n_${row.note.id}"
}

@Composable
private fun NotesSearchField(query: String, resultCount: Int, onQueryChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.messages_search)) },
        )
        if (query.isNotBlank()) {
            Text(
                text = stringResource(R.string.notes_search_results, resultCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(ownerType: Note.OwnerType) {
    val res = headerRes(ownerType) ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(res.second),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(res.first),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Screen-local total map: only the 8 supported owner types get a header (the VM emits no others). */
private fun headerRes(t: Note.OwnerType): Pair<Int, Int>? = when (t) {
    Note.OwnerType.ANNOUNCEMENT -> R.string.notes_type_announcement to R.drawable.ic_announcement
    Note.OwnerType.ATTENDANCE -> R.string.notes_type_attendance to R.drawable.ic_attendance
    Note.OwnerType.BEHAVIOR -> R.string.notes_type_behavior to R.drawable.ic_behavior
    Note.OwnerType.DAY -> R.string.notes_type_day to R.drawable.ic_calendar_day
    Note.OwnerType.EVENT -> R.string.notes_type_event to R.drawable.ic_calendar_event
    Note.OwnerType.GRADE -> R.string.notes_type_grade to R.drawable.ic_grade
    Note.OwnerType.LESSON -> R.string.notes_type_lesson to R.drawable.ic_timetable
    Note.OwnerType.MESSAGE -> R.string.notes_type_message to R.drawable.ic_message
    else -> null
}

@Composable
private fun searchHighlightColor(): Color {
    val context = LocalContext.current
    return remember(context) { Color(R.attr.colorControlHighlight.resolveAttr(context)) }
}
