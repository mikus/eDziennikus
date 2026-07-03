/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.Colors

private val BlockShape = RoundedCornerShape(4.dp)

/**
 * Full-parity Compose port of the legacy `timetable_lesson` view (TimetableDayFragment.buildLessonViews).
 * Renders the subject (optionally colour-backed + strikethrough for cancelled/shifted-source), the lesson
 * number, the change annotation, old→new struck teacher/team/classroom, event dots and the attendance icon.
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

    Box(
        modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(BlockShape)
            .background(subjectColor ?: MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                lesson.displayLessonNumber?.let {
                    Text("$it", style = MaterialTheme.typography.labelSmall, color = onSubjectSecondary)
                    Box(Modifier.width(6.dp))
                }
                Text(
                    text = subjectText(lesson.displaySubjectName ?: "", struck),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSubject,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (block.unseen) {
                    Box(Modifier.width(4.dp))
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.error))
                }
            }

            val annotation = annotationText(block.annotation, lesson)
            if (annotation != null) {
                Text(annotation, style = MaterialTheme.typography.labelSmall, color = onSubjectSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // teacher / team / classroom — old→new strikethrough when the id/value changed
            val teacher = fieldText(unchanged = lesson.teacherId != null && lesson.teacherId == lesson.oldTeacherId, oldVal = lesson.oldTeacherName, newVal = lesson.teacherName)
            val team = fieldText(unchanged = lesson.teamId != null && lesson.teamId == lesson.oldTeamId, oldVal = lesson.oldTeamName, newVal = lesson.teamName)
            val room = fieldText(unchanged = lesson.classroom != null && lesson.classroom == lesson.oldClassroom, oldVal = lesson.oldClassroom, newVal = lesson.classroom)
            val details = listOf(teacher, team, room).filter { it.isNotEmpty() }
            if (details.isNotEmpty()) {
                Text(joinAnnotated(details), style = MaterialTheme.typography.labelSmall, color = onSubjectSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                block.events.forEach { ev ->
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(ev.eventColor)))
                    Box(Modifier.width(3.dp))
                }
                block.attendance?.let { att ->
                    val ctx = LocalContext.current
                    val drawable = attendanceIconFactory(ctx, att)
                    if (drawable != null) {
                        AndroidView(
                            factory = { context -> ImageView(context) },
                            update = { it.setImageDrawable(drawable) },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun subjectText(name: String, struck: Boolean): AnnotatedString = buildAnnotatedString {
    if (struck) withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(name) } else append(name)
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

private fun joinAnnotated(parts: List<AnnotatedString>): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { i, p ->
        if (i > 0) append(", ")
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
