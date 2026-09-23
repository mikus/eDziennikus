/*
 * Copyright (c) Mikolaj Olszewski 2026-9-23.
 */
package eu.mikus.edziennik.ui.agenda.lessonchanges

import eu.mikus.edziennik.data.db.entity.Lesson

/**
 * The view state `timetable_lesson.xml` used to derive from its `annotationVisible` variable.
 *
 * One input drove TWO views — the annotation's visibility and `subjectName`'s `maxLines` — which is
 * exactly the coupling a hand-port drops. Returning both together makes it a single testable fact
 * rather than two lines someone has to remember to keep in step.
 */
internal data class LessonCues(
    val annotationVisible: Boolean,
    val subjectMaxLines: Int,
)

/**
 * Null means "this type sets no cues" — which is today's behaviour, not a new case.
 *
 * The `when` in [LessonChangesAdapter] has no `else`: it handles five types and `TYPE_NO_LESSONS`
 * falls through, so `annotationVisible` is never assigned for it and a recycled row keeps whatever
 * the previous lesson showed. Returning null preserves that exactly while making it explicit rather
 * than accidental. Returning `annotationVisible = true` for unhandled types would be a silent
 * behaviour change smuggled in under a refactor.
 */
internal fun lessonCues(lessonType: Int): LessonCues? = when (lessonType) {
    Lesson.TYPE_NORMAL -> LessonCues(annotationVisible = false, subjectMaxLines = 2)
    Lesson.TYPE_CANCELLED,
    Lesson.TYPE_CHANGE,
    Lesson.TYPE_SHIFTED_SOURCE,
    Lesson.TYPE_SHIFTED_TARGET -> LessonCues(annotationVisible = true, subjectMaxLines = 1)
    else -> null
}
