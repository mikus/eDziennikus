/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.compose.SwipeRefreshScrollBridge

@Composable
fun GradesScreen(
    state: GradesUiState,
    formatters: GradesFormatters,
    onSubjectToggle: (Long) -> Unit,
    onSemesterToggle: (Long, Int) -> Unit,
    onGradeClick: (GradeFull) -> Unit,
    onEditorClick: (Long, Int) -> Unit,
    onItemSeen: (GradeFull) -> Unit,
    setRefreshEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Non-Content states establish a defined refresh baseline (Content's SwipeRefreshScrollBridge then owns it).
    LaunchedEffect(state) { if (state !is GradesUiState.Content) setRefreshEnabled(true) }
    when (state) {
        GradesUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        GradesUiState.Empty -> CenteredMessage(modifier, R.string.grades_no_data)
        GradesUiState.Unsupported -> CenteredMessage(modifier, R.string.grades_university_unsupported)
        is GradesUiState.Content -> GradesList(state, formatters,
            onSubjectToggle, onSemesterToggle, onGradeClick, onEditorClick, onItemSeen, setRefreshEnabled, modifier)
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, res: Int) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(res), style = MaterialTheme.typography.headlineSmall, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun GradesList(
    state: GradesUiState.Content,
    formatters: GradesFormatters,
    onSubjectToggle: (Long) -> Unit,
    onSemesterToggle: (Long, Int) -> Unit,
    onGradeClick: (GradeFull) -> Unit,
    onEditorClick: (Long, Int) -> Unit,
    onItemSeen: (GradeFull) -> Unit,
    setRefreshEnabled: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val listState: LazyListState = rememberLazyListState()
    SwipeRefreshScrollBridge(listState, setRefreshEnabled)
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
        state.subjects.forEach { subject ->
            item(key = "s${subject.subjectId}") {
                SubjectHeader(subject, formatters, onItemSeen) { onSubjectToggle(subject.subjectId) }
            }
            if (subject.expanded) {
                subject.semesters.forEach { semester ->
                    item(key = "s${subject.subjectId}-sem${semester.number}") {
                        SemesterHeader(semester, formatters, onItemSeen,
                            onToggle = { onSemesterToggle(subject.subjectId, semester.number) },
                            onEditor = { onEditorClick(subject.subjectId, semester.number) })
                    }
                    if (semester.expanded) {
                        if (semester.grades.isEmpty()) {
                            item(key = "s${subject.subjectId}-sem${semester.number}-empty") {
                                Text(
                                    stringResource(R.string.grades_no_data),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(semester.grades, key = { "g${it.id}" }) { grade ->
                            GradeRow(grade, formatters, onGradeClick, onItemSeen)
                        }
                    }
                }
            }
        }
        item(key = "stats") { StatsCard(state.stats) }
    }
}

@Composable
private fun SubjectHeader(
    subject: SubjectItem,
    formatters: GradesFormatters,
    onItemSeen: (GradeFull) -> Unit,
    onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (subject.hasUnseen) UnreadDot()
            Text(
                if (subject.isUnknown) stringResource(R.string.grades_subject_unknown) else subject.name,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = if (subject.isUnknown) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.weight(1f),
            )
            Chevron(subject.expanded)
        }
        if (subject.expanded) {
            Text(formatters.yearSummaryText(subject.gradeCount, subject.averages).toString(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProposedFinalRow(subject.proposedGrade, subject.finalGrade, formatters.gradeColor, onItemSeen)
        } else {
            val previewed = subject.semesters.firstOrNull { it.number == subject.firstNonEmptySemesterNumber }
                ?: subject.semesters.firstOrNull()   // matches the builder's "else first" rule for firstNonEmptySemesterNumber
            if (previewed != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        previewed.grades.forEach { g -> GradePill(g, formatters.gradeColor(g)) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        formatters.semesterAverageText(previewed.averages, previewed.number)?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        formatters.yearAverageText(subject.averages)?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ProposedFinalRow(subject.proposedGrade, subject.finalGrade, formatters.gradeColor, onItemSeen)
                }
            }
        }
    }
}

@Composable
private fun SemesterHeader(
    semester: SemesterItem,
    formatters: GradesFormatters,
    onItemSeen: (GradeFull) -> Unit,
    onToggle: () -> Unit,
    onEditor: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (semester.hasUnseen) UnreadDot()
        Text(stringResource(R.string.grades_semester_format, semester.number),
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        formatters.averageText(semester.averages)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp))
        }
        ProposedFinalRow(semester.proposedGrade, semester.finalGrade, formatters.gradeColor, onItemSeen)
        if (!semester.hideEditor) {
            IconicsIcon(CommunityMaterial.Icon.cmd_calculator, contentDescription = null,
                modifier = Modifier.clickable(onClick = onEditor).padding(start = 8.dp))
        }
        Chevron(semester.expanded)
    }
}

@Composable
private fun GradeRow(
    grade: GradeFull,
    formatters: GradesFormatters,
    onGradeClick: (GradeFull) -> Unit,
    onItemSeen: (GradeFull) -> Unit,
) {
    LaunchedEffect(grade.id) { onItemSeen(grade) }
    Row(
        Modifier.fillMaxWidth().clickable { onGradeClick(grade) }.padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (grade.showAsUnseen) UnreadDot()
        GradePill(grade, formatters.gradeColor(grade), big = true)
        val texts = gradeRowTexts(grade.description, grade.category, grade.isImprovement)
        val category = if (texts.categoryIsImprovement)
            stringResource(R.string.grades_improvement_category_format, texts.categoryText)
        else texts.categoryText
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(texts.topText, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                formatters.gradeDateText(grade)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                formatters.weightText(grade)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                if (category.isNotBlank()) {
                    Text(category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                grade.teacherName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProposedFinalRow(
    proposed: GradeFull?,
    final: GradeFull?,
    gradeColor: (Grade) -> Color,
    onItemSeen: (GradeFull) -> Unit,
) {
    if (proposed == null && final == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        proposed?.let {
            LaunchedEffect(it.id) { onItemSeen(it) }
            GradePill(it, gradeColor(it), modifier = Modifier.padding(end = 4.dp))
        }
        final?.let {
            LaunchedEffect(it.id) { onItemSeen(it) }
            GradePill(it, gradeColor(it))
        }
    }
}

// Two independent sections per period (normal + point), each gated separately — mirrors StatsViewHolder.
// Uses every StatsItem field: expected/proposed/final/notAllFinal feed the normal rows + provenance notice
// (ported getSemesterString); point* feed the point rows. No field is dead.
@Composable
private fun StatsCard(stats: StatsItem) {
    val normalRows = listOf(
        NormalStat(stringResource(R.string.grades_semester_format, 1), stats.normalSem1, stats.normalSem1Proposed, stats.normalSem1Final, stats.sem1NotAllFinal),
        NormalStat(stringResource(R.string.grades_semester_format, 2), stats.normalSem2, stats.normalSem2Proposed, stats.normalSem2Final, stats.sem2NotAllFinal),
        NormalStat(stringResource(R.string.grades_stats_yearly), stats.normalYearly, stats.normalYearlyProposed, stats.normalYearlyFinal, stats.yearlyNotAllFinal),
    ).filter { it.expected != 0f || it.proposed != 0f || it.final != 0f }
    val pointRows = listOf(
        stringResource(R.string.grades_semester_format, 1) to stats.pointSem1,
        stringResource(R.string.grades_semester_format, 2) to stats.pointSem2,
        stringResource(R.string.grades_stats_yearly) to stats.pointYearly,
    ).filter { it.second != 0f }

    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.grades_stats_title), style = MaterialTheme.typography.titleMedium)
            if (normalRows.isEmpty() && pointRows.isEmpty()) {
                Text(stringResource(R.string.grades_stats_no_data),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                return@Column
            }
            if (normalRows.isNotEmpty()) {
                Text(stringResource(R.string.grades_stats_normal),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                normalRows.forEach { NormalStatRow(it) }
            }
            if (pointRows.isNotEmpty()) {
                Text(stringResource(R.string.grades_stats_point),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                pointRows.forEach { (label, value) -> StatValueRow(label, "%.2f".format(value), notice = null) }
            }
            Text(stringResource(R.string.grades_stats_disclaimer),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private data class NormalStat(val label: String, val expected: Float, val proposed: Float, val final: Float, val notAllFinal: Boolean)

/** Ports StatsViewHolder.getSemesterString: value = final→proposed→expected; notice describes provenance. */
@Composable
private fun NormalStatRow(s: NormalStat) {
    val value = when {
        s.final != 0f -> s.final
        s.proposed != 0f -> s.proposed
        s.expected != 0f -> s.expected
        else -> return
    }
    val notice = when {
        s.final != 0f -> when {
            s.notAllFinal -> if (s.expected != 0f) stringResource(R.string.grades_stats_from_final, "%.2f".format(s.expected))
                else stringResource(R.string.grades_stats_from_final_no_expected)
            s.proposed != 0f -> stringResource(R.string.grades_stats_proposed_avg, "%.2f".format(s.proposed))
            else -> null
        }
        s.proposed != 0f -> if (s.expected != 0f) stringResource(R.string.grades_stats_from_proposed, "%.2f".format(s.expected))
            else stringResource(R.string.grades_stats_from_proposed_no_expected)
        s.expected != 0f -> stringResource(R.string.grades_stats_expected)
        else -> null
    }
    StatValueRow(s.label, "%.2f".format(value), notice)
}

@Composable
private fun StatValueRow(label: String, value: String, notice: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        notice?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun UnreadDot() {
    Box(Modifier.padding(end = 8.dp).size(8.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFF44336)) }
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    IconicsIcon(
        if (expanded) CommunityMaterial.Icon.cmd_chevron_up else CommunityMaterial.Icon.cmd_chevron_down,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
