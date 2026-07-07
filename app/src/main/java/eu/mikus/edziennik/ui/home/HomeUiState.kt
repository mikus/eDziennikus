/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date

/** Stateless render model for the Home dashboard. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(
        val cards: List<HomeCardUi>,
        val locked: Boolean,
    ) : HomeUiState
}

/** One dashboard card. [cardId] is the Bundle-safe LazyColumn key + persistence id. */
sealed interface HomeCardUi {
    companion object {
        /** Legacy rule: card ids at/above this floor (Archive 101 / Availability 102) are pinned —
         *  not draggable / not removable. Single source for both the UI and HomeCardOrder. */
        const val PINNED_ID_FLOOR = 100
    }

    val cardId: Int
    val pinned: Boolean get() = cardId >= PINNED_ID_FLOOR

    data class LuckyNumber(
        val titleRes: Int,
        val titleArgs: List<Any>,
        val subTextRes: Int,
        val subTextArgs: List<Any>,
        val emojiRes: Int,
    ) : HomeCardUi {
        override val cardId: Int get() = HomeCard.CARD_LUCKY_NUMBER
    }

    data class Events(
        val rows: List<EventFull>,
        val showType: Boolean,
        val showSubject: Boolean,
    ) : HomeCardUi {
        override val cardId: Int get() = HomeCard.CARD_EVENTS
    }

    data class Grades(val subjects: List<SubjectGradeRow>) : HomeCardUi {
        override val cardId: Int get() = HomeCard.CARD_GRADES
    }

    data class Notes(val rows: List<Note>) : HomeCardUi {
        override val cardId: Int get() = HomeCard.CARD_NOTES
    }

    /** Native timetable card (id 2, draggable). Carries the profile-filtered 7-day lesson window +
     *  session-fixed config; the composable resolves the displayed day + owns the live counter. */
    data class Timetable(
        val lessons: List<LessonFull>,
        val notPublic: Boolean,
        val bellSyncDiffMillis: Long,
        val countInSeconds: Boolean,
        val today: Date,
    ) : HomeCardUi {
        override val cardId: Int get() = HomeCard.CARD_TIMETABLE
    }

    /** Archive (101) / Availability (102) — rendered via AndroidView. */
    data class Wrapped(override val cardId: Int) : HomeCardUi
}

/** A subject's recent grades for the Grades card (rendered as a row of GradePill). */
data class SubjectGradeRow(
    val subjectName: String,
    val grades: List<GradeFull>,
)
