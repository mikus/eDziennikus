/*
 * Copyright (c) Mikolaj Olszewski 2026-11-11.
 */
package eu.mikus.edziennik.ui.event

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ui.dialogs.base.FormDropdownItem
import eu.mikus.edziennik.utils.models.Time

/**
 * The rows of [EventManualDialog]'s time dropdown and the [Time] → row lookup, kept free of
 * Android resources and the database so they can be tested on the JVM.
 *
 * Every row's id is unique within its list, because `FormDropdown` displays
 * `items.firstOrNull { it.id == selectedId }`: two rows sharing an id would always display the
 * first. Ids come from three disjoint ranges:
 * - a lesson row is the lesson's timetable primary key, [Lesson.id] (unique per profile and never
 *   negative — see [Lesson.buildId]);
 * - the fixed rows are the constants below, `0` and small negatives;
 * - a custom-time row is [customTimeId], a negative band below every fixed row.
 *
 * A [Lesson.id] is NOT stable across a sync: [Lesson.buildId] hashes the teacher, team, classroom
 * and type, so a substitution re-keys the row. A selection held across a rebuild of the list goes
 * through [reselect], which keeps its id only if that id is still in the new list and otherwise
 * finds the row again by start time and subject.
 */
object EventTimeItems {

    const val ALL_DAY_ID = 0L
    const val CUSTOM_TRIGGER_ID = -1L
    const val NO_TIMETABLE_ID = -2L
    const val NO_LESSONS_ID = -3L

    /**
     * [Time] does not bound its fields. Every string parser reads two digits per field and the picker
     * gives 0–23 / 0–59, so a time this dialog can produce has `value < 1_000_000` and the band stays
     * below every fixed row's id. `Time.fromValue`, which the Room converter uses, divides without
     * bounding, so a corrupt stored value could in principle exceed it — then two custom rows could
     * share an id, which shows the first. Nothing else breaks, and no such value is known to exist.
     */
    private const val CUSTOM_TIME_BASE = 1_000_000L

    fun customTimeId(time: Time): Long = -(CUSTOM_TIME_BASE + time.value)

    /** The localized labels of the fixed rows, resolved by the dialog. */
    data class Labels(
        val allDay: String,
        val customTime: String,
        val noTimetable: String,
        val noLessons: String,
    )

    /** The list after a [select], and the id to select in it. */
    data class Selection(val items: List<FormDropdownItem>, val selectedId: Long)

    /**
     * The time rows for one day's [lessons]: all-day, the custom-time trigger, then a "no timetable"
     * row when [lessons] is empty, else one row per lesson ("no lessons" for a `TYPE_NO_LESSONS` row).
     */
    fun build(lessons: List<LessonFull>, labels: Labels): List<FormDropdownItem> {
        val list = mutableListOf<FormDropdownItem>()
        list += FormDropdownItem(id = ALL_DAY_ID, text = labels.allDay, tag = TimeSelection.AllDay)
        list += FormDropdownItem(id = CUSTOM_TRIGGER_ID, text = labels.customTime)

        if (lessons.isEmpty()) {
            list += FormDropdownItem(NO_TIMETABLE_ID, labels.noTimetable)
            return list
        }

        var noLessonsAdded = false
        lessons.forEach { lesson ->
            if (lesson.type == Lesson.TYPE_NO_LESSONS) {
                // At most one per date in practice (its id is the date), but one row would say it anyway.
                if (!noLessonsAdded) list += FormDropdownItem(NO_LESSONS_ID, labels.noLessons)
                noLessonsAdded = true
                return@forEach
            }
            list += FormDropdownItem(
                id = lesson.id,
                text = lessonLabel(lesson),
                tag = lesson,
            )
        }
        return list
    }

    // Plain-text label: FormDropdownItem.text is a String, so the legacy strikethrough/italic
    // spannables for cancelled/shifted lessons collapse to plain text here.
    private fun lessonLabel(lesson: LessonFull): String {
        val name = lesson.displaySubjectName
        return buildString {
            append(lesson.displayStartTime?.stringHM ?: "")
            if (name != null) {
                append(" - ")
                append(name)
            }
        }
    }

    /**
     * Select [time] in [items]. Of the lesson rows starting at [time], the first match wins:
     * the row of lesson [preferLessonId], a row of subject [preferSubjectId] (an edited event keeps
     * its subject, not its lesson, and the row's label is its start and subject), the first row.
     * With no lesson row at [time], an existing custom row for it is selected, else one is appended.
     *
     * A [preferSubjectId] of 0 or less is "no subject", not a subject to look for: an event with no
     * subject stores `-1` (`EventManualDialog.onPositiveClick`), and the dialog reads that back as
     * null (`selectDefaultSubject`). Without this a subject-less event would prefer a subject-less
     * lesson at its start over the first one.
     */
    fun select(
        items: List<FormDropdownItem>,
        time: Time,
        preferLessonId: Long? = null,
        preferSubjectId: Long? = null,
    ): Selection {
        val atTime = items.filter {
            (it.tag as? LessonFull)?.displayStartTime?.value == time.value
        }
        val lessonRow = atTime.firstOrNull { (it.tag as LessonFull).id == preferLessonId }
            ?: preferSubjectId?.takeIf { it > 0 }?.let { id ->
                atTime.firstOrNull { (it.tag as LessonFull).displaySubjectId == id }
            }
            ?: atTime.firstOrNull()
        if (lessonRow != null) return Selection(items, lessonRow.id)

        val id = customTimeId(time)
        val list = if (items.none { it.id == id })
            items + FormDropdownItem(id = id, text = time.stringHM, tag = TimeSelection.Custom(time))
        else
            items
        return Selection(list, id)
    }

    /**
     * Find [held], a row selected in an earlier list, again in the rebuilt [items]. A row whose id is
     * still there keeps it; a lesson is found by its start time and subject (its id may have changed,
     * see the class KDoc) and is dropped if no lesson starts at that time any more; a custom time is
     * selected again as by [select]. Returns null when nothing should stay selected.
     */
    fun reselect(items: List<FormDropdownItem>, held: FormDropdownItem?): Selection? {
        held ?: return null
        if (items.any { it.id == held.id }) return Selection(items, held.id)
        return when (val tag = held.tag) {
            is LessonFull -> {
                val start = tag.displayStartTime ?: return null
                select(items, start, preferSubjectId = tag.displaySubjectId)
                    .takeIf { sel -> sel.items.first { it.id == sel.selectedId }.tag is LessonFull }
            }
            is TimeSelection.Custom -> select(items, tag.start)
            else -> null
        }
    }
}
