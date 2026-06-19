/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.teachers

import eu.mikus.edziennik.data.db.entity.Subject
import eu.mikus.edziennik.data.db.entity.Teacher

sealed interface TeachersUiState {
    data object Loading : TeachersUiState
    data object Empty : TeachersUiState
    data class Content(
        val rows: List<TeacherRow>,
        val subjects: List<Subject>,
    ) : TeachersUiState
}

/**
 * A teacher plus the precomputed send-message affordance flag. Subject/role display TEXT is NOT on
 * the row — it needs a Context (`Teacher.getTypeText`) and is resolved at the composable edge, with
 * [TeachersUiState.Content.subjects] supplying the snapshot for that lookup.
 */
data class TeacherRow(
    val teacher: Teacher,
    val canSendMessage: Boolean,
)
