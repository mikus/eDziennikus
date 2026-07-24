/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */
package eu.mikus.edziennik.ui.event

import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

/**
 * Which kind of date row this is. The localized label (and, for [OTHER], the date-picker;
 * for [NEXT_LESSON], the DB lookup) is handled at the Compose edge — this model carries
 * only the data the edge needs to render/act.
 */
enum class DateChoiceKind {
    /** "Next lesson of <subject>" — resolved against the timetable DB at the edge. */
    NEXT_LESSON,
    TODAY,
    TOMORROW,
    /** A remaining school day of the current week. */
    THIS_WEEK,
    /** A school day (Mon–Fri) of the next week. */
    NEXT_WEEK,
    /** "Other…" — opens the date picker at the edge. */
    OTHER,
}

/**
 * One row of the date dropdown.
 *
 * @param date the concrete date this row selects; null for [DateChoiceKind.NEXT_LESSON]
 *   (resolved later) and [DateChoiceKind.OTHER] (picked later).
 * @param weekDay the [eu.mikus.edziennik.utils.models.Week] weekday index (MONDAY=0 … SUNDAY=6),
 *   used to build the localized weekday label for [DateChoiceKind.THIS_WEEK] /
 *   [DateChoiceKind.NEXT_WEEK]; null for [NEXT_LESSON] and [OTHER].
 * @param subjectId subject to resolve the next lesson of; only set for [NEXT_LESSON].
 * @param subjectName subject caption for the [NEXT_LESSON] label; only set for [NEXT_LESSON].
 */
data class DateChoice(
    val kind: DateChoiceKind,
    val date: Date? = null,
    val weekDay: Int? = null,
    val subjectId: Long? = null,
    val subjectName: String? = null,
)

/**
 * Polymorphic time selection, mirroring the legacy `TimeDropdown.getSelected()` return values:
 * `0L` → [AllDay], `Pair<Time, Time?>` → [Custom], a selected lesson → [Lesson], `null` → [None].
 */
sealed class TimeSelection {
    /** All-day event (legacy `0L`). Maps to a null [Event.time]. */
    object AllDay : TimeSelection()

    /** A custom / manually-picked time (legacy `Pair<Time, Time?>`). */
    data class Custom(val start: Time, val end: Time? = null) : TimeSelection()

    /**
     * A selected timetable lesson. Carries the cascade IDs so the subject/teacher/team
     * dropdowns can auto-select (see [EventManualLogic.cascadeFor]).
     */
    data class Lesson(
        val startTime: Time,
        val endTime: Time? = null,
        val lessonNumber: Int? = null,
        val subjectId: Long? = null,
        val teacherId: Long? = null,
        val teamId: Long? = null,
    ) : TimeSelection()

    /** Nothing valid selected (legacy `null`). */
    object None : TimeSelection()

    /**
     * The value stored in [Event.time]: null for [AllDay], the start time otherwise.
     * Mirrors legacy `startTime = if (timeSelected == 0L) null else (timeSelected as Pair).first`.
     */
    fun toEventTime(): Time? = when (this) {
        AllDay -> null
        is Custom -> start
        is Lesson -> startTime
        None -> null
    }

    /**
     * Whether the current time selection passes save validation. Mirrors the legacy rule
     * `timeSelected !is Pair && timeSelected != 0L` → error: everything except [None] is valid.
     */
    val isValid: Boolean
        get() = this !is None
}

/**
 * The auto-select decision for the subject/teacher/team dropdowns when a lesson (or a plain
 * date) is chosen. Null fields carry the legacy fallback semantics:
 * - [subjectId] null → deselect the subject dropdown, else select this subject.
 * - [teacherId] null → deselect the teacher dropdown, else select this teacher.
 * - [teamId] null → select the student's class team (`selectTeamClass`), else select this team.
 */
data class CascadeSelection(
    val subjectId: Long?,
    val teacherId: Long?,
    val teamId: Long?,
)

/**
 * The four independent validity flags of the save form. Mirrors `saveEvent` (lines 379–408):
 * a missing date, an invalid time selection, a missing type, or a blank topic each flip a flag.
 */
data class ValidationResult(
    val dateInvalid: Boolean,
    val timeInvalid: Boolean,
    val typeInvalid: Boolean,
    val topicInvalid: Boolean,
) {
    val isValid: Boolean
        get() = !dateInvalid && !timeInvalid && !typeInvalid && !topicInvalid
}

/**
 * Pure, Android-free logic extracted from the legacy `EventManualDialog` cascade. Contains only
 * Context-free computations (no resources, drawables, or `getString`) so it is Jupiter-testable.
 * Localized labels, DB lookups, and dropdown side effects stay at the Compose edge.
 */
object EventManualLogic {

    /**
     * Build the date-choice model list, faithfully porting `DateDropdown.loadItems()` under the
     * exact flags `EventManualDialog` sets (`showWeekDays=false`, `showDays=true`,
     * `showOtherDate=true`):
     *
     * - an optional "next lesson of <subject>" row first (only when [nextLessonSubjectId] != null),
     * - TODAY,
     * - TOMORROW (only Mon–Thu, i.e. weekDay < 4),
     * - the remaining school days of the current week (up to Friday),
     * - all school days (Mon–Fri) of the next week,
     * - "Other…".
     *
     * The input [today] is not mutated (it is cloned internally).
     */
    fun buildDateChoices(
        today: Date,
        nextLessonSubjectId: Long? = null,
        nextLessonSubjectName: String? = null,
    ): List<DateChoice> {
        val choices = mutableListOf<DateChoice>()
        val date = today.clone()
        var weekDay = date.weekDay

        nextLessonSubjectId?.let {
            choices += DateChoice(
                kind = DateChoiceKind.NEXT_LESSON,
                subjectId = it,
                subjectName = nextLessonSubjectName,
            )
        }

        // TODAY
        choices += DateChoice(
            kind = DateChoiceKind.TODAY,
            date = date.clone(),
            weekDay = weekDay,
        )

        // TOMORROW (only on school days before Friday)
        if (weekDay < 4) {
            date.stepForward(0, 0, 1)
            weekDay++
            choices += DateChoice(
                kind = DateChoiceKind.TOMORROW,
                date = date.clone(),
                weekDay = weekDay,
            )
        }

        // REMAINING SCHOOL DAYS OF THE CURRENT WEEK
        while (weekDay < 4) {
            date.stepForward(0, 0, 1)
            weekDay++
            choices += DateChoice(
                kind = DateChoiceKind.THIS_WEEK,
                date = date.clone(),
                weekDay = weekDay,
            )
        }

        // go to next week's Monday
        date.stepForward(0, 0, -weekDay + 7)
        weekDay = 0

        // ALL SCHOOL DAYS OF THE NEXT WEEK
        while (weekDay < 5) {
            choices += DateChoice(
                kind = DateChoiceKind.NEXT_WEEK,
                date = date.clone(),
                weekDay = weekDay,
            )
            date.stepForward(0, 0, 1)
            weekDay++
        }

        // OTHER
        choices += DateChoice(kind = DateChoiceKind.OTHER)
        return choices
    }

    /**
     * Map a selected timetable lesson to a [TimeSelection], mirroring `TimeDropdown.getSelected()`'s
     * `LessonFull` branch: a lesson with no display start time is not a valid time ([TimeSelection.None]).
     */
    fun lessonToTimeSelection(lesson: LessonFull): TimeSelection {
        val start = lesson.displayStartTime ?: return TimeSelection.None
        return TimeSelection.Lesson(
            startTime = start,
            endTime = lesson.displayEndTime,
            lessonNumber = lesson.displayLessonNumber,
            subjectId = lesson.displaySubjectId,
            teacherId = lesson.displayTeacherId,
            teamId = lesson.displayTeamId,
        )
    }

    /**
     * The cascade auto-select decision, mirroring both `onLessonSelected` and `onDateSelected`:
     * a non-null [lesson] contributes its display subject/teacher/team ids; a null [lesson]
     * (a plain date pick) yields all-null (deselect subject, deselect teacher, select class team).
     * See [CascadeSelection] for the per-field fallback semantics.
     */
    fun cascadeFor(lesson: LessonFull?): CascadeSelection = CascadeSelection(
        subjectId = lesson?.displaySubjectId,
        teacherId = lesson?.displayTeacherId,
        teamId = lesson?.displayTeamId,
    )

    /**
     * Compute the four save-form validity flags. Mirrors `saveEvent` lines 379–408:
     * date missing, time selection invalid, type missing, or topic null/blank.
     */
    fun validate(
        date: Date?,
        time: TimeSelection,
        typeId: Long?,
        topic: String?,
    ): ValidationResult = ValidationResult(
        dateInvalid = date == null,
        timeInvalid = !time.isValid,
        typeInvalid = typeId == null,
        topicInvalid = topic.isNullOrBlank(),
    )

    /**
     * The color shown in the swatch and stored as [Event.color]:
     * the user's custom color, falling back to the selected type's color, falling back to
     * [Event.COLOR_DEFAULT]. Mirrors the legacy `customColor ?: type.color ?: COLOR_DEFAULT`.
     *
     * Related state rules handled by the edge:
     * - selecting a different type resets the custom color to null (see [customColorAfterTypeChange]);
     * - opening the dialog to edit seeds the custom color via [seedCustomColor].
     */
    fun swatchColor(customColor: Int?, typeColor: Int?): Int =
        customColor ?: typeColor ?: Event.COLOR_DEFAULT

    /**
     * The custom color to seed when editing an existing event. Mirrors legacy
     * `if (it.color != -1) customColor = it.color`: the `-1` sentinel (and a null stored color)
     * both mean "no custom color".
     */
    fun seedCustomColor(eventColor: Int?): Int? =
        if (eventColor == -1) null else eventColor

    /** Selecting a new type always clears the custom color. Mirrors `onTypeSelected { customColor = null }`. */
    fun customColorAfterTypeChange(): Int? = null
}
