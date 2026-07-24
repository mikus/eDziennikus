/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.Lesson

/** Which top-level field groups a lesson-details dialog shows, driven purely by the lesson type. */
data class LessonDetailVisibility(
    val showShifted: Boolean,
    val showOldFields: Boolean,
    val showCurrentFields: Boolean,
)

fun lessonDetailVisibility(type: Int) = LessonDetailVisibility(
    showShifted = type >= Lesson.TYPE_SHIFTED_SOURCE,
    showOldFields = type < Lesson.TYPE_SHIFTED_SOURCE,
    showCurrentFields = type != Lesson.TYPE_CANCELLED,
)
