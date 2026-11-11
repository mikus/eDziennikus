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
 */
object EventTimeItems {

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
        list += FormDropdownItem(id = 0L, text = labels.allDay, tag = TimeSelection.AllDay)
        list += FormDropdownItem(id = -1L, text = labels.customTime)

        if (lessons.isEmpty()) {
            list += FormDropdownItem(-2L, labels.noTimetable)
            return list
        }

        lessons.forEach { lesson ->
            if (lesson.type == Lesson.TYPE_NO_LESSONS) {
                list += FormDropdownItem(-2L, labels.noLessons)
                return@forEach
            }
            list += FormDropdownItem(
                id = lesson.displayStartTime?.value?.toLong() ?: -1L,
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
     * Select [time] in [items]: an existing row with that time's id, else a new custom-time row
     * appended to the list.
     */
    fun select(items: List<FormDropdownItem>, time: Time): Selection {
        val id = time.value.toLong()
        val list = if (items.none { it.id == id })
            items + FormDropdownItem(id = id, text = time.stringHM, tag = TimeSelection.Custom(time))
        else
            items
        return Selection(list, id)
    }
}
