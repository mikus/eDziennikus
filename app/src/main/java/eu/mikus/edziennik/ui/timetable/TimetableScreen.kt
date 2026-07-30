/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Week

/**
 * Stateless timetable screen: not-public branch, else a [HorizontalPager] over [days] with a compact
 * day header. Each page collects [dayFlow] for its date and renders the grid/state. FAB & bottom-sheet
 * drive it via [requestedDate]; page settles report back via [onPageChanged].
 */
@Composable
fun TimetableScreen(
    notPublic: Boolean,
    days: List<Date>,
    initialIndex: Int,
    lessonHeight: Int,
    colorSubjectName: Boolean,
    requestedDate: Date?,
    onRequestConsumed: () -> Unit,
    onPageChanged: (Date) -> Unit,
    dayFlow: (Date) -> kotlinx.coroutines.flow.Flow<TimetableDayUiState>,
    onLessonClick: (PositionedLesson) -> Unit,
    onSyncClick: (weekStart: String) -> Unit,
    attendanceIconFactory: (android.content.Context, AttendanceFull) -> Drawable?,
) {
    if (notPublic) {
        NotPublicState()
        return
    }
    if (days.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, days.lastIndex)) { days.size }

    // report the settled page's date back to the ViewModel (FAB enable, sync/add-event date)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(days[it]) }
    }
    // Fragment-requested date (Today FAB / date picker) -> scroll the pager
    LaunchedEffect(requestedDate) {
        val target = requestedDate ?: return@LaunchedEffect
        val index = days.indexOfFirst { it.value == target.value }
        if (index != -1) pagerState.animateScrollToPage(index)
        onRequestConsumed()
    }

    Column(Modifier.fillMaxSize()) {
        DayHeader(days[pagerState.currentPage])
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { days[it].value },   // Bundle-safe Int key (Phase-6 lesson)
        ) { page ->
            val date = days[page]
            val state by remember(date.value) { dayFlow(date) }
                .collectAsStateWithLifecycle(TimetableDayUiState.Loading)
            TimetableDayPage(
                date = date,
                state = state,
                lessonHeight = lessonHeight,
                colorSubjectName = colorSubjectName,
                onLessonClick = onLessonClick,
                onSyncClick = onSyncClick,
                attendanceIconFactory = attendanceIconFactory,
            )
        }
    }
}

@Composable
private fun DayHeader(date: Date) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${Week.getFullDayName(date.weekDay)}, ${date.formattedString}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TimetableDayPage(
    date: Date,
    state: TimetableDayUiState,
    lessonHeight: Int,
    colorSubjectName: Boolean,
    onLessonClick: (PositionedLesson) -> Unit,
    onSyncClick: (String) -> Unit,
    attendanceIconFactory: (android.content.Context, AttendanceFull) -> Drawable?,
) {
    when (state) {
        is TimetableDayUiState.Loading -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), Alignment.Center) { CircularProgressIndicator() }
        is TimetableDayUiState.NoTimetable -> CenteredState(
            title = stringResource(R.string.timetable_no_timetable_title),
            body = stringResource(R.string.timetable_no_timetable_text),
            buttonText = stringResource(R.string.timetable_no_timetable_sync),
            onButton = { onSyncClick(state.weekStart) },
        )
        is TimetableDayUiState.NoLessons -> CenteredState(
            title = stringResource(R.string.timetable_no_lessons_title),
            body = null,
            buttonText = if (state.isWeekend) null else stringResource(R.string.refresh),
            onButton = { onSyncClick(state.weekStart) },
        )
        is TimetableDayUiState.Content -> TimetableGrid(
            startHour = state.startHour,
            endHour = state.endHour,
            lessonHeight = lessonHeight,
            blocks = state.blocks,
            isToday = date.value == Date.getToday().value,
            colorSubjectName = colorSubjectName,
            onLessonClick = onLessonClick,
            attendanceIconFactory = attendanceIconFactory,
        )
    }
}

@Composable
private fun CenteredState(title: String, body: String?, buttonText: String?, onButton: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (body != null) {
            Box(Modifier.padding(top = 16.dp)) { Text(body, style = MaterialTheme.typography.bodyLarge) }
        }
        if (buttonText != null) {
            Box(Modifier.padding(top = 16.dp)) { Button(onClick = onButton) { Text(buttonText) } }
        }
    }
}

@Composable
private fun NotPublicState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.timetable_not_public_title), style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.timetable_not_public_text), style = MaterialTheme.typography.bodyLarge) }
        Box(Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.timetable_not_public_hint), style = MaterialTheme.typography.bodyMedium) }
    }
}
