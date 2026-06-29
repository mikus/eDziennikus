/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Grade

private fun isProposed(type: Int) = type == Grade.TYPE_SEMESTER1_PROPOSED ||
    type == Grade.TYPE_SEMESTER2_PROPOSED || type == Grade.TYPE_YEAR_PROPOSED

@Composable
fun GradePill(
    grade: Grade,
    color: Color,
    big: Boolean = false,
    periodTextual: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val proposed = isProposed(grade.type)
    val radius = if (big) 8.dp else 4.dp
    val shape = RoundedCornerShape(radius)
    val text = pillText(grade, periodTextual)
    val textColor = when {
        proposed -> MaterialTheme.colorScheme.onSurface
        color.luminance() > 0.3f -> Color(0xAA000000)
        else -> Color(0xCCFFFFFF)
    }
    val base = if (proposed) {
        modifier.border(1.dp, color, shape)
    } else {
        modifier.background(color, shape)
    }
    Text(
        text = text,
        color = textColor,
        fontSize = if (big) 24.sp else 14.sp,
        maxLines = 1,
        modifier = base.padding(horizontal = if (big) 4.dp else 5.dp, vertical = if (big) 2.dp else 0.dp),
    )
}

@Composable
private fun pillText(grade: Grade, periodTextual: Boolean): String = when {
    periodTextual -> when (grade.type) {
        Grade.TYPE_SEMESTER1_PROPOSED, Grade.TYPE_SEMESTER2_PROPOSED ->
            stringResource(R.string.grade_semester_proposed_format, grade.name)
        Grade.TYPE_SEMESTER1_FINAL, Grade.TYPE_SEMESTER2_FINAL ->
            stringResource(R.string.grade_semester_final_format, grade.name)
        Grade.TYPE_YEAR_PROPOSED -> stringResource(R.string.grade_year_proposed_format, grade.name)
        Grade.TYPE_YEAR_FINAL -> stringResource(R.string.grade_year_final_format, grade.name)
        else -> grade.name
    }
    grade.name.isBlank() -> "  "
    else -> grade.name
}
