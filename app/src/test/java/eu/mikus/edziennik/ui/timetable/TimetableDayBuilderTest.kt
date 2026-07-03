/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TimetableDayBuilderTest {

    private val date = Date(2026, 6, 1)      // Monday
    private val saturday = Date(2026, 6, 6)  // Saturday
    private val cfg = TimetableDayBuilder.Config(
        trimHourRange = false,
        showEvents = true,
        showAttendance = true,
        defaultStartHour = 6,
        defaultEndHour = 19,
    )

    private fun lesson(
        type: Int = Lesson.TYPE_NORMAL,
        start: Time? = Time(8, 0, 0),
        end: Time? = Time(8, 45, 0),
        seen: Boolean = true,
        subjectId: Long? = 1, oldSubjectId: Long? = 1,
        teacherId: Long? = 1, oldTeacherId: Long? = 1,
        teamId: Long? = 1, oldTeamId: Long? = 1,
        classroom: String? = "12", oldClassroom: String? = "12",
        oldSubjectName: String? = "Old", oldTeacherName: String? = "OldT",
        lessonDate: Date? = date, oldDate: Date? = date,
    ): LessonFull = mockk(relaxed = true) {
        every { this@mockk.type } returns type
        every { displayStartTime } returns start
        every { displayEndTime } returns end
        every { this@mockk.startTime } returns start
        every { this@mockk.seen } returns seen
        every { this@mockk.subjectId } returns subjectId
        every { this@mockk.oldSubjectId } returns oldSubjectId
        every { this@mockk.teacherId } returns teacherId
        every { this@mockk.oldTeacherId } returns oldTeacherId
        every { this@mockk.teamId } returns teamId
        every { this@mockk.oldTeamId } returns oldTeamId
        every { this@mockk.classroom } returns classroom
        every { this@mockk.oldClassroom } returns oldClassroom
        every { this@mockk.oldSubjectName } returns oldSubjectName
        every { this@mockk.oldTeacherName } returns oldTeacherName
        every { this@mockk.date } returns lessonDate
        every { this@mockk.oldDate } returns oldDate
    }

    private fun event(time: Time?): EventFull = mockk(relaxed = true) {
        every { this@mockk.time } returns time
    }

    private fun attendance(start: Time): AttendanceFull = mockk(relaxed = true) {
        every { startTime } returns start
    }

    @Test
    fun `empty lessons yields NoTimetable with week start`() {
        val state = TimetableDayBuilder.build(date, emptyList(), emptyList(), emptyList(), cfg)
        val s = assertIs<TimetableDayUiState.NoTimetable>(state)
        assertEquals("2026-06-01", s.weekStart)   // Monday of the week
    }

    @Test
    fun `single NO_LESSONS marker yields NoLessons, weekday not weekend`() {
        val state = TimetableDayBuilder.build(
            date, listOf(lesson(type = Lesson.TYPE_NO_LESSONS)), emptyList(), emptyList(), cfg,
        )
        val s = assertIs<TimetableDayUiState.NoLessons>(state)
        assertFalse(s.isWeekend)
    }

    @Test
    fun `NO_LESSONS on saturday is weekend`() {
        val state = TimetableDayBuilder.build(
            saturday, listOf(lesson(type = Lesson.TYPE_NO_LESSONS)), emptyList(), emptyList(), cfg,
        )
        assertTrue(assertIs<TimetableDayUiState.NoLessons>(state).isWeekend)
    }

    @Test
    fun `normal lessons produce Content with widened default hour range`() {
        val state = TimetableDayBuilder.build(
            date,
            listOf(lesson(start = Time(8, 0, 0), end = Time(8, 45, 0))),
            emptyList(), emptyList(), cfg,
        )
        val c = assertIs<TimetableDayUiState.Content>(state)
        assertEquals(6, c.startHour)   // default kept (8 > 6)
        assertEquals(19, c.endHour)    // default kept (9 < 19)
        assertEquals(1, c.blocks.size)
        assertEquals(480, c.blocks[0].startMinute)
        assertEquals(525, c.blocks[0].endMinute)
    }

    @Test
    fun `trim hour range clamps to lesson bounds`() {
        val state = TimetableDayBuilder.build(
            date,
            listOf(lesson(start = Time(8, 0, 0), end = Time(9, 30, 0))),
            emptyList(), emptyList(),
            cfg.copy(trimHourRange = true),
        )
        val c = assertIs<TimetableDayUiState.Content>(state)
        assertEquals(8, c.startHour)
        assertEquals(10, c.endHour)    // endHour = max end hour + 1
    }

    @Test
    fun `events match by display start time, capped at three, gated by config`() {
        val l = lesson(start = Time(8, 0, 0))
        val events = listOf(event(Time(8, 0, 0)), event(Time(8, 0, 0)), event(Time(8, 0, 0)), event(Time(8, 0, 0)), event(Time(9, 0, 0)))
        val c = TimetableDayBuilder.build(date, listOf(l), events, emptyList(), cfg) as TimetableDayUiState.Content
        assertEquals(3, c.blocks[0].events.size)

        val off = TimetableDayBuilder.build(date, listOf(l), events, emptyList(), cfg.copy(showEvents = false)) as TimetableDayUiState.Content
        assertTrue(off.blocks[0].events.isEmpty())
    }

    @Test
    fun `attendance matches by raw start time, gated by config`() {
        val l = lesson(start = Time(8, 0, 0))
        val att = listOf(attendance(Time(8, 0, 0)), attendance(Time(9, 0, 0)))
        val c = TimetableDayBuilder.build(date, listOf(l), emptyList(), att, cfg) as TimetableDayUiState.Content
        assertEquals(att[0], c.blocks[0].attendance)

        val off = TimetableDayBuilder.build(date, listOf(l), emptyList(), att, cfg.copy(showAttendance = false)) as TimetableDayUiState.Content
        assertNull(off.blocks[0].attendance)
    }

    @Test
    fun `unseen is true only for non-normal unseen lessons`() {
        val normalUnseen = lesson(type = Lesson.TYPE_NORMAL, seen = false)
        val changeUnseen = lesson(type = Lesson.TYPE_CHANGE, seen = false)
        val changeSeen = lesson(type = Lesson.TYPE_CHANGE, seen = true)
        val c = TimetableDayBuilder.build(date, listOf(normalUnseen, changeUnseen, changeSeen), emptyList(), emptyList(), cfg) as TimetableDayUiState.Content
        val byType = c.blocks.associate { it.lesson.type to it.unseen }
        // both TYPE_CHANGE map to same key; assert via find instead:
        assertFalse(c.blocks.first { it.lesson === normalUnseen }.unseen)
        assertTrue(c.blocks.first { it.lesson === changeUnseen }.unseen)
        assertFalse(c.blocks.first { it.lesson === changeSeen }.unseen)
    }

    @Test
    fun `annotation - cancelled`() {
        val c = TimetableDayBuilder.build(date, listOf(lesson(type = Lesson.TYPE_CANCELLED)), emptyList(), emptyList(), cfg) as TimetableDayUiState.Content
        assertEquals(LessonAnnotation.Cancelled, c.blocks[0].annotation)
    }

    @Test
    fun `annotation - change flags which fields differ by id`() {
        val l = lesson(
            type = Lesson.TYPE_CHANGE,
            subjectId = 2, oldSubjectId = 1,     // subject changed
            teacherId = 1, oldTeacherId = 1,     // teacher same
            teamId = 3, oldTeamId = 1,           // team changed
            classroom = "9", oldClassroom = "12", // classroom changed (string compare)
        )
        val c = TimetableDayBuilder.build(date, listOf(l), emptyList(), emptyList(), cfg) as TimetableDayUiState.Content
        val a = assertIs<LessonAnnotation.Changed>(c.blocks[0].annotation)
        assertTrue(a.subject)
        assertFalse(a.teacher)
        assertTrue(a.team)
        assertTrue(a.classroom)
    }

    @Test
    fun `annotation - shifted source and target`() {
        val src = lesson(type = Lesson.TYPE_SHIFTED_SOURCE)
        val tgt = lesson(type = Lesson.TYPE_SHIFTED_TARGET)
        val c = TimetableDayBuilder.build(date, listOf(src, tgt), emptyList(), emptyList(), cfg) as TimetableDayUiState.Content
        assertTrue(assertIs<LessonAnnotation.Shifted>(c.blocks.first { it.lesson === src }.annotation).isSource)
        assertFalse(assertIs<LessonAnnotation.Shifted>(c.blocks.first { it.lesson === tgt }.annotation).isSource)
    }

    @Test
    fun `annotation - normal lesson is None`() {
        val c = TimetableDayBuilder.build(date, listOf(lesson()), emptyList(), emptyList(), cfg) as TimetableDayUiState.Content
        assertEquals(LessonAnnotation.None, c.blocks[0].annotation)
    }
}
