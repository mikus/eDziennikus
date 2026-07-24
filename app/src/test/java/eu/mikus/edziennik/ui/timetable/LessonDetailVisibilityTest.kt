/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.Lesson
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class LessonDetailVisibilityTest {

    @Test
    fun normal_showsOldAndCurrent_noShift() {
        val v = lessonDetailVisibility(Lesson.TYPE_NORMAL)
        assertTrue(v.showCurrentFields)
        assertTrue(v.showOldFields)
        assertFalse(v.showShifted)
    }

    @Test
    fun cancelled_hidesCurrent_keepsOld_noShift() {
        val v = lessonDetailVisibility(Lesson.TYPE_CANCELLED)
        assertFalse(v.showCurrentFields)
        assertTrue(v.showOldFields)
        assertFalse(v.showShifted)
    }

    @Test
    fun change_showsOldAndCurrent_noShift() {
        val v = lessonDetailVisibility(Lesson.TYPE_CHANGE)
        assertTrue(v.showCurrentFields)
        assertTrue(v.showOldFields)
        assertFalse(v.showShifted)
    }

    @Test
    fun shiftedSource_showsShift_hidesOld_keepsCurrent() {
        val v = lessonDetailVisibility(Lesson.TYPE_SHIFTED_SOURCE)
        assertTrue(v.showCurrentFields)
        assertFalse(v.showOldFields)
        assertTrue(v.showShifted)
    }

    @Test
    fun shiftedTarget_showsShift_hidesOld_keepsCurrent() {
        val v = lessonDetailVisibility(Lesson.TYPE_SHIFTED_TARGET)
        assertTrue(v.showCurrentFields)
        assertFalse(v.showOldFields)
        assertTrue(v.showShifted)
    }
}
