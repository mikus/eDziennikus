/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ui.compose.SwipeRefreshScrollBridge
import eu.mikus.edziennik.ui.event.EventRow
import eu.mikus.edziennik.utils.models.Date
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AgendaScreen(
    state: AgendaUiState,
    startMonth: YearMonth,
    endMonth: YearMonth,
    onDaySelected: (Date) -> Unit,
    onEventClick: (EventFull) -> Unit,
    onEventEditClick: (EventFull) -> Unit,
    onItemSeen: (EventFull) -> Unit,
    onLessonChangesClick: (Date) -> Unit,
    onTeacherAbsenceClick: (Date) -> Unit,
    setRefreshEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state) { if (state !is AgendaUiState.Content) setRefreshEnabled(true) }
    when (state) {
        AgendaUiState.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        AgendaUiState.Empty -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.agenda_no_events_day))
        }
        is AgendaUiState.Content -> AgendaContent(
            state = state,
            startMonth = startMonth,
            endMonth = endMonth,
            onDaySelected = onDaySelected,
            onEventClick = onEventClick,
            onEventEditClick = onEventEditClick,
            onItemSeen = onItemSeen,
            onLessonChangesClick = onLessonChangesClick,
            onTeacherAbsenceClick = onTeacherAbsenceClick,
            setRefreshEnabled = setRefreshEnabled,
            modifier = modifier,
        )
    }
}

@Composable
private fun AgendaContent(
    state: AgendaUiState.Content,
    startMonth: YearMonth,
    endMonth: YearMonth,
    onDaySelected: (Date) -> Unit,
    onEventClick: (EventFull) -> Unit,
    onEventEditClick: (EventFull) -> Unit,
    onItemSeen: (EventFull) -> Unit,
    onLessonChangesClick: (Date) -> Unit,
    onTeacherAbsenceClick: (Date) -> Unit,
    setRefreshEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = state.selectedDate.toYearMonth(),
        firstDayOfWeek = firstDayOfWeekFromLocale(),
    )
    val listState = rememberLazyListState()
    SwipeRefreshScrollBridge(listState, setRefreshEnabled)

    Column(modifier.fillMaxSize()) {
        HorizontalCalendar(
            state = calendarState,
            dayContent = { day ->
                DayCell(
                    day = day,
                    dots = state.monthDots[day.date.toAppDate()],
                    selected = day.position == DayPosition.MonthDate && day.date == state.selectedDate.toLocalDate(),
                    onClick = { onDaySelected(day.date.toAppDate()) },
                )
            },
            monthHeader = { month ->
                val locale = ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.ROOT
                val ym = month.yearMonth
                val label = ym.month
                    .getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    .replaceFirstChar { it.titlecase(locale) }
                Text(
                    "$label ${ym.year}",
                    Modifier.fillMaxWidth().padding(12.dp),
                    fontWeight = FontWeight.Medium,
                )
            },
        )
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (state.dayItems.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.agenda_no_events_day),
                        Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.dayItems, key = { it.lazyKey }) { item ->
                when (item) {
                    is AgendaItem.EventItem -> EventRow(
                        event = item.event,
                        unseen = item.unseen,
                        onClick = onEventClick,
                        onEditClick = onEventEditClick,
                        onAppear = onItemSeen,
                    )
                    is AgendaItem.LessonChangesItem -> SummaryRow(
                        stringResource(R.string.agenda_lesson_changes_summary, item.count),
                    ) { onLessonChangesClick(item.date) }
                    is AgendaItem.TeacherAbsenceItem -> SummaryRow(
                        stringResource(R.string.agenda_teacher_absence_summary, item.count),
                    ) { onTeacherAbsenceClick(item.date) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, dots: DayDots?, selected: Boolean, onClick: () -> Unit) {
    val inMonth = day.position == DayPosition.MonthDate
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .clickable(enabled = inMonth, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (inMonth && dots?.hasUnseen == true) FontWeight.Bold else null,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (inMonth && dots != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (dots.colors.isEmpty()) {
                        Dot(MaterialTheme.colorScheme.outline)            // change/absence-only marker
                    } else {
                        dots.colors.take(3).forEach { Dot(Color(it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

@Composable
private fun SummaryRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider()
}
