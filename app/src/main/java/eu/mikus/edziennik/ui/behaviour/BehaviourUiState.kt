/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.behaviour

import eu.mikus.edziennik.data.db.full.NoticeFull

/**
 * Semester filter modes. [semester] == null means "whole year" (no filter); otherwise it is the
 * `NoticeFull.semester` value to keep. Mirrors the legacy displayMode 0/1/2.
 */
enum class SemesterFilter(val semester: Int?) {
    YEAR(null),
    SEMESTER_1(1),
    SEMESTER_2(2),
}

/** Raw counts only — the `warnings >= 3` emphasis is a Screen-side styling decision. */
data class BehaviourSummary(
    val praises: Int,
    val warnings: Int,
    val other: Int,
)

sealed interface BehaviourUiState {
    data object Loading : BehaviourUiState

    /**
     * The filtered notices (possibly empty), the count summary, and the active filter. Behaviour
     * folds "empty" into [Content] (no separate Empty state) because the summary header + filter
     * control must stay visible even when the filtered list is empty.
     */
    data class Content(
        val notices: List<NoticeFull>,
        val summary: BehaviourSummary,
        val filter: SemesterFilter,
    ) : BehaviourUiState
}
