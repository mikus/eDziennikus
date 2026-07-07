/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ui.compose.SwipeRefreshScrollBridge
import eu.mikus.edziennik.ui.event.EventRow
import eu.mikus.edziennik.ui.grades.GradePill
import eu.mikus.edziennik.ui.notes.NoteCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HomeScreen(
    state: HomeUiState,
    onReorder: (fromId: Int, toId: Int) -> Unit,
    onRemove: (cardId: Int) -> Unit,
    onConfigureCards: () -> Unit,
    gradeColor: (Grade) -> Color,
    onLuckyClick: () -> Unit,
    onEventClick: (EventFull) -> Unit,
    onEventEditClick: (EventFull) -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenGrades: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onAddNote: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenTimetable: () -> Unit,
    onTimetableBellSync: () -> Unit,
    onTimetableFullscreen: () -> Unit,
    onTimetableSync: (String) -> Unit,
    wrappedCardContent: @Composable (cardId: Int) -> Unit,
    setRefreshEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state as? HomeUiState.Content
    if (content == null) {        // Loading: keep blank but allow pull-to-refresh
        LaunchedEffect(Unit) { setRefreshEnabled(true) }
        return
    }

    if (content.cards.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(if (content.locked) R.string.home_configure_locked else R.string.home_configure_cards),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Button(onClick = onConfigureCards) { Text(stringResource(R.string.home_configure_cards)) }
        }
        LaunchedEffect(Unit) { setRefreshEnabled(true) }
        return
    }

    val listState = rememberLazyListState()
    SwipeRefreshScrollBridge(listState, setRefreshEnabled)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onReorder(from.key as Int, to.key as Int)
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(content.cards, key = { it.cardId }) { card ->
            ReorderableItem(reorderState, key = card.cardId) { _ ->
                val draggable = !card.pinned && !content.locked
                val cardModifier = if (draggable) Modifier.longPressDraggableHandle() else Modifier
                if (draggable) {
                    val dismiss = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismiss.settledValue) {
                        if (dismiss.settledValue != SwipeToDismissBoxValue.Settled) onRemove(card.cardId)
                    }
                    SwipeToDismissBox(state = dismiss, backgroundContent = {}) {
                        HomeCardItem(card, cardModifier, gradeColor, onLuckyClick, onEventClick, onEventEditClick,
                            onOpenAgenda, onOpenGrades, onNoteClick, onAddNote, onOpenNotes,
                            onOpenTimetable, onTimetableBellSync, onTimetableFullscreen, onTimetableSync, wrappedCardContent)
                    }
                } else {
                    HomeCardItem(card, cardModifier, gradeColor, onLuckyClick, onEventClick, onEventEditClick,
                        onOpenAgenda, onOpenGrades, onNoteClick, onAddNote, onOpenNotes,
                        onOpenTimetable, onTimetableBellSync, onTimetableFullscreen, onTimetableSync, wrappedCardContent)
                }
            }
        }
    }
}

@Composable
private fun HomeCardItem(
    card: HomeCardUi,
    modifier: Modifier,
    gradeColor: (Grade) -> Color,
    onLuckyClick: () -> Unit,
    onEventClick: (EventFull) -> Unit,
    onEventEditClick: (EventFull) -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenGrades: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onAddNote: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenTimetable: () -> Unit,
    onTimetableBellSync: () -> Unit,
    onTimetableFullscreen: () -> Unit,
    onTimetableSync: (String) -> Unit,
    wrappedCardContent: @Composable (cardId: Int) -> Unit,
) {
    when (card) {
        is HomeCardUi.LuckyNumber -> LuckyNumberCard(card, modifier, onLuckyClick)
        is HomeCardUi.Events -> EventsCard(card, modifier, onEventClick, onEventEditClick, onOpenAgenda)
        is HomeCardUi.Grades -> GradesCard(card, modifier, gradeColor, onOpenGrades)
        is HomeCardUi.Notes -> NotesCard(card, modifier, onNoteClick, onAddNote, onOpenNotes)
        is HomeCardUi.Timetable -> TimetableHomeCard(
            card, onOpenTimetable, onTimetableBellSync, onTimetableFullscreen, onTimetableSync, modifier,
        )
        is HomeCardUi.Wrapped -> Box(modifier.fillMaxWidth()) { wrappedCardContent(card.cardId) }
    }
}

@Composable
private fun LuckyNumberCard(card: HomeCardUi.LuckyNumber, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(card.emojiRes), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(card.titleRes, *card.titleArgs.toTypedArray()), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(card.subTextRes, *card.subTextArgs.toTypedArray()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventsCard(card: HomeCardUi.Events, modifier: Modifier, onEventClick: (EventFull) -> Unit, onEventEditClick: (EventFull) -> Unit, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (card.rows.isEmpty()) {
                Text(stringResource(R.string.home_card_no_events), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else card.rows.forEach { event ->
                EventRow(
                    event = event, unseen = false, showTime = false, showType = card.showType,
                    showSubject = card.showSubject, onClick = onEventClick, onEditClick = onEventEditClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradesCard(card: HomeCardUi.Grades, modifier: Modifier, gradeColor: (Grade) -> Color, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card.subjects.isEmpty()) {
                Text(stringResource(R.string.home_card_no_grades), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else card.subjects.forEach { subject ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    subject.grades.forEach { g -> GradePill(grade = g, color = gradeColor(g), periodTextual = true) }
                    Text(
                        stringResource(R.string.grade_subject_format, subject.subjectName),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(card: HomeCardUi.Notes, modifier: Modifier, onNoteClick: (Note) -> Unit, onAddNote: () -> Unit, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (card.rows.isEmpty()) {
                Text(stringResource(R.string.home_card_no_notes), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else card.rows.forEach { note -> NoteCard(note = note, onNoteClick = onNoteClick) }
            Button(onClick = onAddNote, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.add)) }
        }
    }
}
