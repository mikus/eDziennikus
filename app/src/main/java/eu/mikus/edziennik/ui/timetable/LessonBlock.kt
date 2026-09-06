/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ext.resolveAttr
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.compose.toAnnotatedString
import eu.mikus.edziennik.utils.Colors

private val BlockShape = RoundedCornerShape(6.dp)
private val AnnotationShape = RoundedCornerShape(4.dp)

/**
 * Full-parity Compose port of the legacy `timetable_lesson` view (TimetableDayFragment.buildLessonViews):
 * a big bold subject with the large lesson number + attendance check top-right, the exact time range and
 * teacher • team below, optional colour-backed background + strikethrough for cancelled/shifted-source,
 * the change annotation, old→new struck teacher/team/classroom, and event dots.
 */
@Composable
fun LessonBlock(
    block: PositionedLesson,
    colorSubjectName: Boolean,
    onClick: () -> Unit,
    attendanceIconFactory: (Context, AttendanceFull) -> Drawable?,
    modifier: Modifier = Modifier,
) {
    val lesson = block.lesson
    val struck = lesson.type == Lesson.TYPE_CANCELLED || lesson.type == Lesson.TYPE_SHIFTED_SOURCE

    val subjectColor: Color? = if (colorSubjectName) {
        Color(lesson.color ?: Colors.stringToMaterialColorCRC(lesson.displaySubjectName ?: ""))
    } else null
    val onSubject: Color = when {
        subjectColor == null -> MaterialTheme.colorScheme.onSurface
        subjectColor.luminance() > 0.5f -> Color(0xFF000000)
        else -> Color(0xFFFFFFFF)
    }
    val onSubjectSecondary: Color = when {
        subjectColor == null -> MaterialTheme.colorScheme.onSurfaceVariant
        subjectColor.luminance() > 0.5f -> Color(0xFF666666)
        else -> Color(0xFFAAAAAA)
    }

    val annotation = annotationText(block.annotation, lesson)

    Box(
        modifier
            .fillMaxSize()
            .padding(vertical = 2.dp)
            .clip(BlockShape)
            .background(subjectColor ?: MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top row: subject (grows) + unread dot + attendance check + big lesson number
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (lesson.hasNotes()) {
                    val noteIcon = if (lesson.hasReplacingNotes())
                        CommunityMaterial.Icon3.cmd_swap_horizontal
                    else
                        CommunityMaterial.Icon3.cmd_playlist_edit
                    IconicsIcon(noteIcon, contentDescription = null, sizeDp = 16, tint = onSubjectSecondary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = subjectText(
                        lesson.getNoteSubstituteText(showNotes = true) ?: lesson.displaySubjectName ?: "",
                        struck,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (struck) onSubjectSecondary else onSubject,
                    maxLines = if (annotation != null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (block.unseen) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.error))
                }
                block.attendance?.let { att ->
                    val ctx = LocalContext.current
                    val drawable = attendanceIconFactory(ctx, att)
                    if (drawable != null) {
                        Spacer(Modifier.width(8.dp))
                        AndroidView(
                            factory = { context -> ImageView(context) },
                            update = { it.setImageDrawable(drawable) },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                lesson.displayLessonNumber?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$it",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = onSubject,
                    )
                }
            }

            if (annotation != null) {
                Text(
                    text = annotation,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF000000),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(AnnotationShape)
                        .background(annotationColor(block.annotation))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }

            // Exact time range (+ classroom old→new when present)
            val timeLine = timeText(lesson)
            if (timeLine.isNotEmpty()) {
                Text(
                    text = timeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSubject,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Teacher • team (old→new struck when changed), with event dots pinned to the end
            val teacher = fieldText(
                unchanged = lesson.teacherId != null && lesson.teacherId == lesson.oldTeacherId,
                oldVal = lesson.oldTeacherName, newVal = lesson.teacherName,
            )
            val team = fieldText(
                unchanged = lesson.teamId != null && lesson.teamId == lesson.oldTeamId,
                oldVal = lesson.oldTeamName, newVal = lesson.teamName,
            )
            val teacherTeam = joinAnnotated(listOf(teacher, team).filter { it.isNotEmpty() }, " • ")
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (teacherTeam.isNotEmpty()) {
                    Text(
                        text = teacherTeam,
                        style = MaterialTheme.typography.bodySmall,
                        color = onSubjectSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                block.events.forEach { ev ->
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(ev.eventColor)))
                }
            }
        }
    }
}

/** The subject line — or the replacing note that stands in for it — struck through when cancelled/shifted away. */
private fun subjectText(name: CharSequence, struck: Boolean): AnnotatedString = buildAnnotatedString {
    val text = (name as? Spanned)?.toAnnotatedString() ?: AnnotatedString(name.toString())
    if (struck) withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text) } else append(text)
}

/**
 * The legacy annotation chip's fill, which TimetableManager.getAnnotation picked per change type from the
 * four `timetable_lesson_*_color` theme attrs. Keeping it is what lets a cancellation be told from a
 * substitution at a glance, and keeps the grid agreeing with the still-live LessonChangesAdapter.
 */
@Composable
private fun annotationColor(annotation: LessonAnnotation): Color {
    val attr = when (annotation) {
        LessonAnnotation.None -> return Color.Transparent
        LessonAnnotation.Cancelled -> R.attr.timetable_lesson_cancelled_color
        is LessonAnnotation.Changed -> R.attr.timetable_lesson_change_color
        is LessonAnnotation.Shifted ->
            if (annotation.isSource) R.attr.timetable_lesson_shifted_source_color
            else R.attr.timetable_lesson_shifted_target_color
    }
    return Color(attr.resolveAttr(LocalContext.current))
}

/** "H:MM - H:MM" from the lesson's display times, plus the classroom (old→new when changed). */
private fun timeText(lesson: LessonFull): AnnotatedString = buildAnnotatedString {
    val start = lesson.displayStartTime?.stringHM
    val end = lesson.displayEndTime?.stringHM
    if (start != null && end != null) append("$start - $end")
    val room = fieldText(
        unchanged = lesson.classroom != null && lesson.classroom == lesson.oldClassroom,
        oldVal = lesson.oldClassroom, newVal = lesson.classroom,
    )
    if (room.isNotEmpty()) {
        if (length > 0) append(" • ")
        append(room)
    }
}

/** "old" struck-through, then "new" — or just the single value when unchanged. Empty if nothing. */
private fun fieldText(unchanged: Boolean, oldVal: String?, newVal: String?): AnnotatedString = buildAnnotatedString {
    if (unchanged) {
        append(newVal ?: "")
        return@buildAnnotatedString
    }
    if (oldVal != null) withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(oldVal) }
    if (oldVal != null && newVal != null) append(" → ")
    if (newVal != null) append(newVal)
}

private fun joinAnnotated(parts: List<AnnotatedString>, separator: String): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { i, p ->
        if (i > 0) append(separator)
        append(p)
    }
}

@Composable
private fun annotationText(annotation: LessonAnnotation, lesson: LessonFull): String? = when (annotation) {
    LessonAnnotation.None -> null
    LessonAnnotation.Cancelled -> stringResource(R.string.timetable_lesson_cancelled)
    is LessonAnnotation.Changed -> changedText(annotation, lesson)
    is LessonAnnotation.Shifted -> shiftedText(annotation)
}

/**
 * Port of the legacy TimetableManager.getAnnotation TYPE_CHANGE matrix: prefer "Zastępstwo: zamiast %s"
 * naming the replaced subject/teacher (both, then subject, then teacher) when the old name is known,
 * falling back to the plain "Zastępstwo". The old names live on [lesson], not on the pure annotation type.
 */
@Composable
private fun changedText(a: LessonAnnotation.Changed, lesson: LessonFull): String {
    val oldSubject = lesson.oldSubjectName
    val oldTeacher = lesson.oldTeacherName
    return when {
        a.subject && a.teacher && oldSubject != null && oldTeacher != null ->
            stringResource(R.string.timetable_lesson_change_format, "$oldSubject, $oldTeacher")
        a.subject && oldSubject != null ->
            stringResource(R.string.timetable_lesson_change_format, oldSubject)
        a.teacher && oldTeacher != null ->
            stringResource(R.string.timetable_lesson_change_format, oldTeacher)
        else -> stringResource(R.string.timetable_lesson_change)
    }
}

@Composable
private fun shiftedText(a: LessonAnnotation.Shifted): String = when {
    a.isSource && a.otherDateArg != null -> stringResource(R.string.timetable_lesson_shifted_other_day, a.otherDateArg, a.otherTimeArg ?: "")
    a.isSource && a.otherTimeArg != null -> stringResource(R.string.timetable_lesson_shifted_same_day, a.otherTimeArg)
    a.isSource -> stringResource(R.string.timetable_lesson_shifted)
    a.otherDateArg != null -> stringResource(R.string.timetable_lesson_shifted_from_other_day, a.otherDateArg, a.otherTimeArg ?: "")
    a.otherTimeArg != null -> stringResource(R.string.timetable_lesson_shifted_from_same_day, a.otherTimeArg)
    else -> stringResource(R.string.timetable_lesson_shifted_from)
}
