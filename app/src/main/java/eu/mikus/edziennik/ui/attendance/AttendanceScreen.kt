/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import android.text.Spanned
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.entity.AttendanceType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.compose.UnreadDot
import eu.mikus.edziennik.ui.compose.toAnnotatedString
import eu.mikus.edziennik.utils.models.Week
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    period: Period,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    colorForType: (AttendanceType) -> Color,
    icon: (Attendance) -> IIcon?,
    onPeriodChange: (Period) -> Unit,
    onNodeToggle: (NodeKey) -> Unit,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AttendanceUiState.Loading -> Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), Alignment.Center) {
            CircularProgressIndicator()
        }
        AttendanceUiState.Empty -> Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), Alignment.Center) {
            Text(stringResource(R.string.attendances_no_data))
        }
        is AttendanceUiState.Content -> ContentTabs(
            tabs = state.tabs,
            period = period,
            useSymbols = useSymbols,
            colorForAttendance = colorForAttendance,
            colorForType = colorForType,
            icon = icon,
            onPeriodChange = onPeriodChange,
            onNodeToggle = onNodeToggle,
            onLeafClick = onLeafClick,
            onItemSeen = onItemSeen,
            initialPage = initialPage,
            onPageChange = onPageChange,
            modifier = modifier,
        )
    }
}

private val TAB_TITLES = listOf(
    R.string.attendance_tab_summary,
    R.string.attendance_tab_days,
    R.string.attendance_tab_months,
    R.string.attendance_tab_types,
    R.string.attendance_tab_list,
)

@Composable
private fun ContentTabs(
    tabs: List<AttendanceTab>,
    period: Period,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    colorForType: (AttendanceType) -> Color,
    icon: (Attendance) -> IIcon?,
    onPeriodChange: (Period) -> Unit,
    onNodeToggle: (NodeKey) -> Unit,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, tabs.lastIndex)) { tabs.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
    }

    Column(modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
            TAB_TITLES.forEachIndexed { index, titleRes ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(titleRes)) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val listState = rememberLazyListState()
            when (val tab = tabs[page]) {
                is AttendanceTab.SummaryTab -> SummaryTabContent(
                    tab, period, listState, useSymbols, colorForAttendance, colorForType, icon,
                    onPeriodChange, onNodeToggle, onLeafClick, onItemSeen,
                )
                is AttendanceTab.DaysTab -> DaysTabContent(
                    tab, listState, useSymbols, colorForAttendance, icon, onNodeToggle, onLeafClick, onItemSeen,
                )
                is AttendanceTab.MonthsTab -> MonthsTabContent(
                    tab, listState, useSymbols, colorForAttendance, colorForType, icon,
                    onNodeToggle, onLeafClick, onItemSeen,
                )
                is AttendanceTab.TypesTab -> TypesTabContent(
                    tab, listState, useSymbols, colorForAttendance, colorForType, icon,
                    onNodeToggle, onLeafClick, onItemSeen,
                )
                is AttendanceTab.ListTab -> ListTabContent(
                    tab, listState, useSymbols, colorForAttendance, icon, onLeafClick, onItemSeen,
                )
            }
        }
    }
}

/* ---------- shared chrome ---------- */

@Composable
private fun HeaderRow(
    title: String,
    expanded: Boolean,
    hasUnseen: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (expanded) "▾" else "▸", fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        if (hasUnseen) UnreadDot(Modifier.padding(end = 8.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        trailing()
    }
}

@Composable
private fun LeafRow(
    leaf: AttendanceLeaf,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    icon: (Attendance) -> IIcon?,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
) {
    val att = leaf.attendance
    LaunchedEffect(att.id) { onItemSeen(att) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onLeafClick(att) }
            .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttendancePill(
            text = pillText(att.typeSymbol, att.typeShort, useSymbols),
            color = colorForAttendance(att),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SubjectText(att, leaf.unseen)
            Text(att.typeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = listOfNotNull(
                    Week.getFullDayName(att.date.weekDay),
                    att.date.formattedStringShort,
                    att.startTime?.stringHM,
                    att.lessonNumber?.let { stringResource(R.string.attendance_lesson_number_format, it) },
                ).joinToString(" • "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        icon(att)?.let { IconicsIcon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
    }
    HorizontalDivider(Modifier.padding(start = 32.dp))
}

/**
 * The subject line, in-Compose analog of AttendanceViewHolder's `getNoteSubstituteText` +
 * [eu.mikus.edziennik.utils.managers.NoteManager.Companion.prependIcon] pair (same shape as
 * `EventRow.EventTopicText`): a replacing note stands in for the subject name, and a note of either kind
 * is flagged by a leading glyph — swap-horizontal when it replaces the original, playlist-edit otherwise.
 * Notes are unconditionally shown because both legacy hosts used the adapter default `showNotes = true`.
 */
@Composable
private fun SubjectText(att: AttendanceFull, unseen: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (att.hasNotes()) {
            val noteIcon = if (att.hasReplacingNotes())
                CommunityMaterial.Icon3.cmd_swap_horizontal
            else
                CommunityMaterial.Icon3.cmd_playlist_edit
            IconicsIcon(noteIcon, contentDescription = null, sizeDp = 18, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        val text = remember(att) {
            val raw: CharSequence = att.getNoteSubstituteText(true) ?: att.subjectLongName ?: att.lessonTopic.orEmpty()
            (raw as? Spanned)?.toAnnotatedString() ?: AnnotatedString(raw.toString())
        }
        Text(text = text, fontWeight = if (unseen) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun pillText(symbol: String, short: String, useSymbols: Boolean): String =
    (if (useSymbols) symbol else short).ifBlank { "  " }

@Composable
private fun PercentText(fraction: Float?) {
    Text(
        text = if (fraction == null) "—" else "${(fraction * 100f).toInt()}%",
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun NoneRow() {
    Text(
        stringResource(R.string.attendances_no_data),
        Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/* ---------- per-tab content ---------- */

@Composable
private fun SummaryTabContent(
    tab: AttendanceTab.SummaryTab,
    period: Period,
    listState: LazyListState,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    colorForType: (AttendanceType) -> Color,
    icon: (Attendance) -> IIcon?,
    onPeriodChange: (Period) -> Unit,
    onNodeToggle: (NodeKey) -> Unit,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeriodChip(R.string.summary_mode_year, period == Period.ALL) { onPeriodChange(Period.ALL) }
                PeriodChip(R.string.summary_mode_semester_1, period == Period.SEM1) { onPeriodChange(Period.SEM1) }
                PeriodChip(R.string.summary_mode_semester_2, period == Period.SEM2) { onPeriodChange(Period.SEM2) }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                val pct = tab.stats.overallPercent
                if (pct != null) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.size(96.dp),
                        )
                        Text("${(pct * 100f).toInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("—", fontSize = 20.sp)
                }
            }
        }
        item {
            AttendanceBarChart(
                segments = tab.stats.counts.byType.map { Segment(colorForType(it.type), it.count) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            PreviewPills(tab.stats.counts, useSymbols, colorForType)
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
        items(tab.subjects, key = { it.key.stableId }) { subject ->
            HeaderRow(
                title = subject.name,
                expanded = subject.expanded,
                hasUnseen = subject.hasUnseen,
                onClick = { onNodeToggle(subject.key) },
            ) { PercentText(subject.percentage) }
            AttendanceBarChart(
                segments = subject.counts.byType.map { Segment(colorForType(it.type), it.count) },
                modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
            )
            if (subject.expanded) {
                if (subject.leaves.isEmpty()) NoneRow()
                subject.leaves.forEach {
                    LeafRow(it, useSymbols, colorForAttendance, icon, onLeafClick, onItemSeen)
                }
            }
        }
    }
}

@Composable
private fun PreviewPills(
    counts: CountSnapshot,
    useSymbols: Boolean,
    colorForType: (AttendanceType) -> Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counts.byType.forEach { tc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttendancePill(pillText(tc.type.typeSymbol, tc.type.typeShort, useSymbols), colorForType(tc.type))
                Spacer(Modifier.width(4.dp))
                Text(tc.count.toString(), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DaysTabContent(
    tab: AttendanceTab.DaysTab,
    listState: LazyListState,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    icon: (Attendance) -> IIcon?,
    onNodeToggle: (NodeKey) -> Unit,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(tab.dayRanges, key = { it.key.stableId }) { range ->
            val start = "${range.rangeStart.day}.${range.rangeStart.month}.${range.rangeStart.year}"
            val end = "${range.rangeEnd.day}.${range.rangeEnd.month}.${range.rangeEnd.year}"
            HeaderRow(
                title = if (start == end) start else "$start – $end",
                expanded = range.expanded,
                hasUnseen = range.hasUnseen,
                onClick = { onNodeToggle(range.key) },
            ) { }
            if (!range.expanded) {
                val preview = range.leaves.filter {
                    it.attendance.baseType != Attendance.TYPE_PRESENT_CUSTOM && it.attendance.baseType != Attendance.TYPE_UNKNOWN
                }
                if (preview.isEmpty()) {
                    Text(
                        stringResource(R.string.attendance_empty_text),
                        Modifier.padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        preview.forEach {
                            AttendancePill(pillText(it.attendance.typeSymbol, it.attendance.typeShort, useSymbols), colorForAttendance(it.attendance))
                        }
                    }
                }
            } else {
                range.leaves.forEach {
                    LeafRow(it, useSymbols, colorForAttendance, icon, onLeafClick, onItemSeen)
                }
            }
        }
    }
}

@Composable
private fun MonthsTabContent(
    tab: AttendanceTab.MonthsTab,
    listState: LazyListState,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    colorForType: (AttendanceType) -> Color,
    icon: (Attendance) -> IIcon?,
    onNodeToggle: (NodeKey) -> Unit,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(tab.months, key = { it.key.stableId }) { month ->
            HeaderRow(
                title = "${month.month}.${month.year}",
                expanded = month.expanded,
                hasUnseen = month.hasUnseen,
                onClick = { onNodeToggle(month.key) },
            ) { PercentText(month.percentage) }
            AttendanceBarChart(
                segments = month.counts.byType.map { Segment(colorForType(it.type), it.count) },
                modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            if (month.expanded) {
                if (month.leaves.isEmpty()) NoneRow()
                month.leaves.forEach {
                    LeafRow(it, useSymbols, colorForAttendance, icon, onLeafClick, onItemSeen)
                }
            } else {
                PreviewPills(month.counts, useSymbols, colorForType)
            }
        }
    }
}

@Composable
private fun TypesTabContent(
    tab: AttendanceTab.TypesTab,
    listState: LazyListState,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    colorForType: (AttendanceType) -> Color,
    icon: (Attendance) -> IIcon?,
    onNodeToggle: (NodeKey) -> Unit,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(tab.types, key = { it.key.stableId }) { type ->
            Row(
                Modifier.fillMaxWidth().clickable { onNodeToggle(type.key) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (type.expanded) "▾" else "▸", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                if (type.hasUnseen) UnreadDot(Modifier.padding(end = 8.dp))
                AttendancePill(
                    pillText(type.type.typeSymbol, type.type.typeShort, useSymbols),
                    colorForType(type.type),
                    big = true,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.type.typeName, fontWeight = FontWeight.Medium)
                    Text(
                        listOf(
                            stringResource(R.string.attendance_percentage_format, (type.sharePercent ?: 0f) * 100f),
                            stringResource(R.string.attendance_type_yearly_format, type.yearCount),
                            stringResource(R.string.attendance_type_semester_format, type.semesterCount),
                        ).joinToString(" • "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (type.expanded) {
                type.leaves.forEach {
                    LeafRow(it, useSymbols, colorForAttendance, icon, onLeafClick, onItemSeen)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ListTabContent(
    tab: AttendanceTab.ListTab,
    listState: LazyListState,
    useSymbols: Boolean,
    colorForAttendance: (Attendance) -> Color,
    icon: (Attendance) -> IIcon?,
    onLeafClick: (AttendanceFull) -> Unit,
    onItemSeen: (AttendanceFull) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(tab.leaves, key = { it.attendance.id }) {
            LeafRow(it, useSymbols, colorForAttendance, icon, onLeafClick, onItemSeen)
        }
    }
}

@Composable
private fun PeriodChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(labelRes)) })
}
