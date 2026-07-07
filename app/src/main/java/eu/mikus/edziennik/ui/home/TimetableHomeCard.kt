/*
 * Copyright (c) Mikolaj Olszewski 2026-7-6.
 */

package eu.mikus.edziennik.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ext.MINUTE
import eu.mikus.edziennik.ext.compareTo
import eu.mikus.edziennik.ext.timeLeft
import eu.mikus.edziennik.ext.timeTill
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.utils.models.Time
import kotlinx.coroutines.delay

@Composable
fun TimetableHomeCard(
    card: HomeCardUi.Timetable,
    onOpenTimetable: () -> Unit,          // navigate to the Timetable screen (resolved day)
    onBellSync: () -> Unit,
    onFullscreen: () -> Unit,
    onSync: (weekStart: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (card.notPublic) {
        // static "not public" message (title + text), no ticker
        Card(onClick = onOpenTimetable, modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_timetable_not_public), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_timetable_not_public_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    var now by remember { mutableStateOf(syncedNow(card.bellSyncDiffMillis)) }
    LaunchedEffect(card.lessons) {
        while (true) {
            now = syncedNow(card.bellSyncDiffMillis)
            delay(1_000L)   // 1s: enough for the seconds counter; ~half the legacy 500ms wakeups
        }
    }
    val state = remember(card.lessons, now) { TimetableHomeBuilder.build(card.lessons, now, card.today) }

    Card(onClick = onOpenTimetable, modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (state) {
                is TimetableHomeUiState.NoTimetable -> {
                    Text(stringResource(R.string.home_timetable_no_timetable), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_timetable_no_timetable_text, state.weekStart), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onSync(state.weekStart) }) {
                        Text(stringResource(R.string.home_timetable_no_timetable_sync))
                    }
                }
                is TimetableHomeUiState.NoLessons -> {
                    Text(stringResource(R.string.home_timetable_no_lessons), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_timetable_no_lessons_text), style = MaterialTheme.typography.bodyMedium)
                }
                is TimetableHomeUiState.Content -> TimetableContent(state, now, card.countInSeconds, onBellSync, onFullscreen)
            }
        }
    }
}

@Composable
private fun TimetableContent(
    state: TimetableHomeUiState.Content, now: Time, countInSeconds: Boolean,
    onBellSync: () -> Unit, onFullscreen: () -> Unit,
) {
    val ctx = LocalContext.current
    val dayInfo = when (state.mode) {
        TimetableHomeUiState.Mode.TODAY -> stringResource(R.string.home_timetable_today)
        TimetableHomeUiState.Mode.TOMORROW -> stringResource(R.string.home_timetable_tomorrow, *state.dayInfoArgs.toTypedArray())
        TimetableHomeUiState.Mode.THIS_WEEK -> stringResource(R.string.home_timetable_date_this_week, *state.dayInfoArgs.toTypedArray())
        TimetableHomeUiState.Mode.FUTURE -> stringResource(R.string.home_timetable_date_future, *state.dayInfoArgs.toTypedArray())
    }
    val lessonInfo = if (state.mode == TimetableHomeUiState.Mode.TODAY)
        stringResource(R.string.home_timetable_lessons_remaining, state.lessonCount, state.lastEnd ?: "?")
    else
        stringResource(R.string.home_timetable_lessons_info, state.lessonCount, state.firstStart ?: "?", state.lastEnd ?: "?")

    // "ongoing" is the builder's single decision (it set showAllLessons = !ongoing in TODAY mode, and
    // re-runs each tick) — derive it, don't recompute the now/counter comparison a second time here.
    val ongoing = state.mode == TimetableHomeUiState.Mode.TODAY && !state.showAllLessons
    val bigRes = when {
        state.mode != TimetableHomeUiState.Mode.TODAY -> R.string.home_timetable_lesson_first
        ongoing -> R.string.home_timetable_lesson_ongoing
        else -> R.string.home_timetable_lesson_not_started
    }
    val subject = lessonSubject(state.firstLesson)

    // counter: live (TODAY window) vs static firstStart (future)
    val counterText: String?
    val progress: Pair<Int, Int>?
    val counterStart = state.counterStart
    val counterEnd = state.counterEnd
    if (counterStart != null && counterEnd != null) {
        when {
            now < counterStart -> {
                val diff = counterStart - now                 // Long seconds
                counterText = if (diff >= 60 * MINUTE) counterStart.stringHM else ctx.timeTill(diff.toInt(), "\n", countInSeconds)
                progress = null
            }
            now <= counterEnd -> {
                counterText = ctx.timeLeft((counterEnd - now).toInt(), "\n", countInSeconds)
                progress = (now - counterStart).toInt() to (counterEnd - counterStart).toInt()   // (current, max)
            }
            else -> { counterText = null; progress = null }   // lesson over — next tick re-resolves the day
        }
    } else {
        counterText = state.firstStart; progress = null
    }

    // Header row: day-info + lesson-info (left) ; bell-sync + fullscreen icons (right)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(dayInfo, style = MaterialTheme.typography.titleMedium)
            Text(lessonInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onBellSync) {
            IconicsIcon(SzkolnyFont.Icon.szf_alarm_bell_outline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onFullscreen) {
            IconicsIcon(CommunityMaterial.Icon2.cmd_fullscreen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Big first-lesson row: "<label> <subject>" + classroom + counter/progress
    val bigLessonLabel = stringResource(bigRes)   // e.g. "Teraz: %s"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(bigLessonText(bigLessonLabel, subject), style = MaterialTheme.typography.titleLarge)
            state.firstLesson.displayClassroom?.let { classroom ->
                Text(classroom, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        counterText?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
    progress?.let { (current, max) ->
        if (max > 0) {
            LinearProgressIndicator(progress = { current / max.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }

    // "next lessons" section
    Text(
        stringResource(if (state.showAllLessons) R.string.home_timetable_all_lessons else R.string.home_timetable_later),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (state.nextLessons.isEmpty()) {
        Text(stringResource(R.string.home_timetable_later_no_lessons), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else state.nextLessons.forEach { next ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            next.startHM?.let { hm ->
                Text(hm, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
            }
            Text(lessonSubject(next.lesson), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Substitutes the styled [subject] into a "…%s…" [label] string, preserving the subject's spans. */
private fun bigLessonText(label: String, subject: AnnotatedString): AnnotatedString = buildAnnotatedString {
    val idx = label.indexOf("%s")
    if (idx < 0) {
        append(label)
    } else {
        append(label.substring(0, idx))
        append(subject)
        append(label.substring(idx + 2))
    }
}

/** Port of legacy subjectSpannable → AnnotatedString. */
private fun lessonSubject(lesson: LessonFull?): AnnotatedString = buildAnnotatedString {
    if (lesson == null) { append("?"); return@buildAnnotatedString }
    val name = lesson.displaySubjectName ?: "?"
    when {
        lesson.hasReplacingNotes() -> append(lesson.getNoteSubstituteText(showNotes = true)?.toString() ?: "?")
        lesson.isCancelled -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(name) }
        lesson.isChange -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(name) }
        else -> append(name)
    }
}

private fun syncedNow(bellSyncDiffMillis: Long): Time =
    Time.fromMillis(Time.getNow().inMillis - bellSyncDiffMillis)
