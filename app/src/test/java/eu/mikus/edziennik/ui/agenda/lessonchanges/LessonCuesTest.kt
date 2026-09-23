/*
 * Copyright (c) Mikolaj Olszewski 2026-9-23.
 */

package eu.mikus.edziennik.ui.agenda.lessonchanges

import eu.mikus.edziennik.data.db.entity.Lesson
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the view state that `timetable_lesson.xml` used to derive from its `annotationVisible`
 * binding variable, now that the derivation lives in Kotlin.
 *
 * The move is not compile-gated: a wrong cue set compiles and renders the wrong row.
 */
class LessonCuesTest {

    @Test
    fun `a normal lesson hides the annotation and allows two lines`() {
        assertEquals(
            LessonCues(annotationVisible = false, subjectMaxLines = 2),
            lessonCues(Lesson.TYPE_NORMAL),
        )
    }

    /**
     * The coupling this file exists for.
     *
     * In XML, `annotationVisible` drove two attributes — the annotation's `visibility` and
     * `subjectName`'s `maxLines`. A port that moved the visibility and forgot the `maxLines` cap
     * would pass every other test here and silently let long subject names wrap onto a second line
     * on exactly the rows that also show an annotation. Asserting both halves of the pair, for
     * every type that shows an annotation, is what catches that.
     */
    @Test
    fun `showing the annotation also caps the subject to one line`() {
        val annotated = listOf(
            Lesson.TYPE_CANCELLED,
            Lesson.TYPE_CHANGE,
            Lesson.TYPE_SHIFTED_SOURCE,
            Lesson.TYPE_SHIFTED_TARGET,
        )
        for (type in annotated) {
            assertEquals(
                LessonCues(annotationVisible = true, subjectMaxLines = 1),
                lessonCues(type),
                "lesson type $type",
            )
        }
    }

    /**
     * Pins today's else-less behaviour rather than a designed one.
     *
     * The `when` in `LessonChangesAdapter` has no `else`, so an unhandled type — `TYPE_NO_LESSONS`
     * is the only one — leaves both views untouched and a recycled row keeps the previous lesson's
     * cues. `lessonCues` returns null so the adapter's `?.let` reproduces that. A future "tidy-up"
     * that returns a real cue set here would change what a recycled row renders, which this catches.
     */
    @Test
    fun `an unhandled lesson type sets no cues`() {
        assertNull(lessonCues(Lesson.TYPE_NO_LESSONS))
    }
}
