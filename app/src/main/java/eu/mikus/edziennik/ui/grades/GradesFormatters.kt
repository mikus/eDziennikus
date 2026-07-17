/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.grades

import androidx.compose.ui.graphics.Color
import eu.mikus.edziennik.data.db.entity.Grade

/**
 * The Context/GradesManager-bound display formatters the stateless [GradesScreen] needs, built once by the
 * host (the only App.* reader). Keeps the screen decoupled from GradesManager (the ratified edge pattern).
 */
data class GradesFormatters(
    val gradeColor: (Grade) -> Color,
    val averageText: (AveragesSnapshot) -> String?,
    val semesterAverageText: (AveragesSnapshot, Int) -> String?,
    val yearAverageText: (AveragesSnapshot) -> String?,
    val yearSummaryText: (Int, AveragesSnapshot) -> CharSequence,
    val weightText: (Grade) -> String?,
    val gradeDateText: (Grade) -> String?,
)
