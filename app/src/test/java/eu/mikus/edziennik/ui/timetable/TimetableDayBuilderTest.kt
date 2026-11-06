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
import kotlin.test.assertSame
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
        // Explicit because the helper is a relaxed mock: unstubbed, every instance returns id 0, so
        // any test keyed on identity silently matches every lesson at once.
        id: Long = 0L,
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
        every { this@mockk.id } returns id
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

    private fun attendance(start: Time?, subjectId: Long = -1L, teacherId: Long = -1L): AttendanceFull = mockk(relaxed = true) {
        every { startTime } returns start
        every { this@mockk.subjectId } returns subjectId
        every { this@mockk.teacherId } returns teacherId
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
    fun `attendance matches by display start time, gated by config`() {
        val l = lesson(start = Time(8, 0, 0))
        val att = listOf(attendance(Time(8, 0, 0)), attendance(Time(9, 0, 0)))
        val c = TimetableDayBuilder.build(date, listOf(l), emptyList(), att, cfg) as TimetableDayUiState.Content
        assertEquals(att[0], c.blocks[0].attendance)

        val off = TimetableDayBuilder.build(date, listOf(l), emptyList(), att, cfg.copy(showAttendance = false)) as TimetableDayUiState.Content
        assertNull(off.blocks[0].attendance)
    }

    @Test
    fun `an attendance with no start time is drawn on no lesson`() {
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0)),
            cancelled(2, Time(9, 0, 0), Time(9, 45, 0)),
            normal(3, Time(10, 0, 0), Time(10, 45, 0)),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(attendance(null)), cfg) as TimetableDayUiState.Content
        c.blocks.forEach { assertNull(it.attendance) }
    }

    @Test
    fun `a cancelled lesson shows the attendance recorded in its slot`() {
        val a8 = attendance(Time(8, 0, 0))
        val a9 = attendance(Time(9, 0, 0))
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0)),
            normal(2, Time(9, 0, 0), Time(9, 45, 0)),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(a8, a9), cfg) as TimetableDayUiState.Content
        assertSame(a8, c.blocks[0].attendance)
        assertSame(a9, c.blocks[1].attendance)
    }

    @Test
    fun `a shifted-away lesson shows its own slot's attendance, never its target's`() {
        val a10 = attendance(Time(10, 0, 0))
        val a8 = attendance(Time(8, 0, 0))
        val l = shiftedSource(1, Time(8, 0, 0), Time(8, 45, 0), Time(10, 0, 0), Time(10, 45, 0))
        val c = TimetableDayBuilder.build(date, listOf(l), emptyList(), listOf(a10, a8), cfg) as TimetableDayUiState.Content
        assertSame(a8, c.blocks[0].attendance)
        // Same-day shift: pins that oldDate == date in the fixture, as parseLesson stores it.
        assertEquals(LessonAnnotation.Shifted(isSource = true, otherDateArg = null, otherTimeArg = "10:00"), c.blocks[0].annotation)

        val onlyTarget = TimetableDayBuilder.build(date, listOf(l), emptyList(), listOf(a10), cfg) as TimetableDayUiState.Content
        assertNull(onlyTarget.blocks[0].attendance)
    }

    @Test
    fun `a co-slotted mark goes only to the lesson whose subject it carries`() {
        val mY = attendance(Time(8, 0, 0), subjectId = 22)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 22),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mY), cfg) as TimetableDayUiState.Content
        assertNull(c.blocks[0].attendance)
        assertSame(mY, c.blocks[1].attendance)
    }

    @Test
    fun `a co-slotted mark matching no lesson shows on every one of them`() {
        val mZ = attendance(Time(8, 0, 0), subjectId = 33)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 22),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mZ), cfg) as TimetableDayUiState.Content
        assertSame(mZ, c.blocks[0].attendance)
        assertSame(mZ, c.blocks[1].attendance)
    }

    @Test
    fun `an unknown subject never claims a co-slotted mark`() {
        val unknown = attendance(Time(8, 0, 0), subjectId = -1)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = -1),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 22),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(unknown), cfg) as TimetableDayUiState.Content
        assertSame(unknown, c.blocks[0].attendance)
        assertSame(unknown, c.blocks[1].attendance)

        // The first mark matching no block is the orphan; the unknown mark is not the -1 block's own.
        val mZ = attendance(Time(8, 0, 0), subjectId = 33)
        val both = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mZ, unknown), cfg) as TimetableDayUiState.Content
        assertSame(mZ, both.blocks[0].attendance)
        assertSame(mZ, both.blocks[1].attendance)

        // Zero is unknown too: a block with subject 0 does not claim a mark with subject 0.
        val m0 = attendance(Time(8, 0, 0), subjectId = 0)
        val zeroLessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 0),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 22),
        )
        val zero = TimetableDayBuilder.build(date, zeroLessons, emptyList(), listOf(mZ, m0), cfg) as TimetableDayUiState.Content
        assertSame(mZ, zero.blocks[0].attendance)
        assertSame(mZ, zero.blocks[1].attendance)
    }

    @Test
    fun `a lone lesson keeps its slot's mark whatever its subject`() {
        val mY = attendance(Time(8, 0, 0), subjectId = 22)
        // A later lesson with the mark's subject must not count towards the 08:00 slot.
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(9, 0, 0), Time(9, 45, 0), subject = 22),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mY), cfg) as TimetableDayUiState.Content
        assertSame(mY, c.blocks[0].attendance)
    }

    @Test
    fun `a lone lesson prefers its own subject's mark over input order`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val mY = attendance(Time(8, 0, 0), subjectId = 22)
        val lessons = listOf(normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11))
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mY, mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
    }

    @Test
    fun `a cancelled lesson keeps a co-slotted mark carrying its own subject`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 22),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
        assertNull(c.blocks[1].attendance)
    }

    @Test
    fun `each of two co-slotted lessons gets its own mark`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val mY = attendance(Time(8, 0, 0), subjectId = 22)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 22),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mY, mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
        assertSame(mY, c.blocks[1].attendance)
    }

    @Test
    fun `a same-subject mark goes to the lesson that ran, not the cancelled one`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertNull(c.blocks[0].attendance)
        assertSame(mX, c.blocks[1].attendance)
    }

    @Test
    fun `a same-subject mark stays on every lesson when none of them ran`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            cancelled(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
        assertSame(mX, c.blocks[1].attendance)
    }

    @Test
    fun `two same-subject lessons that both ran both keep its mark`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
        assertSame(mX, c.blocks[1].attendance)
    }

    @Test
    fun `two same-subject lessons that both ran show a mark only on the block of its teacher`() {
        // One subject split into groups: the mark names no team (the Attendances endpoint carries
        // none, and its Lesson resolves to a whole-class assignment even on a group lesson), but
        // the teacher who recorded it is the one teaching the group's block.
        val m1 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 101)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m1), cfg) as TimetableDayUiState.Content
        assertSame(m1, c.blocks[0].attendance)
        assertNull(c.blocks[1].attendance)
    }

    @Test
    fun `each same-subject block shows the mark of its own teacher`() {
        val m1 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 101)
        val m2 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 102)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m1, m2), cfg) as TimetableDayUiState.Content
        assertSame(m1, c.blocks[0].attendance)
        assertSame(m2, c.blocks[1].attendance)
    }

    @Test
    fun `a block prefers its own teacher's mark over an unclaimed one listed first`() {
        // Without the preference both blocks take m9, the first mark of their subject, and m2 -
        // claimed by block 2's teacher - is drawn nowhere.
        val m9 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 109)
        val m2 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 102)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m9, m2), cfg) as TimetableDayUiState.Content
        assertSame(m9, c.blocks[0].attendance)
        assertSame(m2, c.blocks[1].attendance)
    }

    @Test
    fun `a mark whose teacher no same-subject block carries stays on all of them`() {
        // The load-bearing guard. A mark may be recorded by someone teaching neither block (a
        // homeroom teacher correcting it, say); a rule that hid it on any teacher mismatch would
        // draw it on neither block, the outcome the user rejected.
        val m9 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 109)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m9), cfg) as TimetableDayUiState.Content
        assertSame(m9, c.blocks[0].attendance)
        assertSame(m9, c.blocks[1].attendance)
    }

    @Test
    fun `a mark of unknown teacher is never narrowed`() {
        // Holds without reading -1 or 0 back as "unknown": no block's teacher is ever either.
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 102),
        )
        for (unknown in listOf(-1L, 0L)) {
            val m = attendance(Time(8, 0, 0), subjectId = 11, teacherId = unknown)
            val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m), cfg) as TimetableDayUiState.Content
            assertSame(m, c.blocks[0].attendance, "teacherId $unknown")
            assertSame(m, c.blocks[1].attendance, "teacherId $unknown")
        }
    }

    @Test
    fun `the teacher never hands a mark to a block that gave it up for one that ran`() {
        // The teacher stage narrows only among blocks the cancelled-vs-ran stage left the mark on.
        // Weighed against every block of the subject, the cancelled block 1 would claim m1 and give
        // it up at once, so neither block would show it.
        val m1 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 101)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m1), cfg) as TimetableDayUiState.Content
        assertNull(c.blocks[0].attendance)
        assertSame(m1, c.blocks[1].attendance)
    }

    @Test
    fun `a block that did not run is matched on the teacher it had in the slot`() {
        // A cancelled row stores its teacher only in oldTeacherId, so a rule reading teacherId
        // would see no teacher on either block and narrow nothing.
        val m1 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 101)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            cancelled(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m1), cfg) as TimetableDayUiState.Content
        assertSame(m1, c.blocks[0].attendance)
        assertNull(c.blocks[1].attendance)
    }

    @Test
    fun `the teacher never narrows a mark onto a block of another subject`() {
        // An orphan keeps showing on every block that has no mark of its own, whoever recorded it.
        val mZ = attendance(Time(8, 0, 0), subjectId = 33, teacherId = 101)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 22, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mZ), cfg) as TimetableDayUiState.Content
        assertSame(mZ, c.blocks[0].attendance)
        assertSame(mZ, c.blocks[1].attendance)
    }

    @Test
    fun `a lone lesson keeps its first same-subject mark whoever recorded it`() {
        // The teacher stage tells blocks apart; with one block there is nothing to tell apart, so
        // which of two same-subject marks a lone block shows must not change.
        val m9 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 109)
        val m1 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 101)
        val lessons = listOf(normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101))
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m9, m1), cfg) as TimetableDayUiState.Content
        assertSame(m9, c.blocks[0].attendance)
    }

    @Test
    fun `a teacher claims a mark only in its own slot`() {
        // Group A's teacher at 08:00 must not claim a 09:00 mark away from group B's lone block.
        val m = attendance(Time(9, 0, 0), subjectId = 11, teacherId = 101)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(9, 0, 0), Time(9, 45, 0), subject = 11, teacher = 102),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m), cfg) as TimetableDayUiState.Content
        assertNull(c.blocks[0].attendance)
        assertSame(m, c.blocks[1].attendance)
    }

    @Test
    fun `a co-slotted block of unknown teacher gives up a mark another block's teacher claims`() {
        val m1 = attendance(Time(8, 0, 0), subjectId = 11, teacherId = 101)
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = 101),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11, teacher = null),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(m1), cfg) as TimetableDayUiState.Content
        assertSame(m1, c.blocks[0].attendance)
        assertNull(c.blocks[1].attendance)
    }

    @Test
    fun `a shifted-away lesson gives a same-subject mark to the lesson that ran`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            shiftedSource(1, Time(8, 0, 0), Time(8, 45, 0), Time(10, 0, 0), Time(10, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertNull(c.blocks[0].attendance)
        assertSame(mX, c.blocks[1].attendance)
    }

    @Test
    fun `a lesson shifted into a cancelled slot counts as having run`() {
        // `ran` is `!isCancelled`, not "is TYPE_NORMAL". A substitution moved into a freed slot is
        // TYPE_SHIFTED_TARGET, so narrowing `ran` to TYPE_NORMAL would make neither block count as
        // having run and hand the mark back to the cancelled one.
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            shiftedTarget(2, Time(10, 0, 0), Time(10, 45, 0), Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertNull(c.blocks[0].attendance)
        assertSame(mX, c.blocks[1].attendance)
    }

    @Test
    fun `the lesson that ran keeps its mark when it is listed before the cancelled one`() {
        // The slot's owner map collapses with `any`, not last-wins: every other same-subject test
        // lists the block that ran LAST, so a plain `.toMap()` would pass them all. Within one slot
        // the DAO's order is content-derived (TimetableDao orders by id, and inside a slot that is a
        // hash), so either order is reachable on real data.
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
        assertNull(c.blocks[1].attendance)
    }

    @Test
    fun `a cancelled lesson that loses its own mark still shows an orphan`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val mZ = attendance(Time(8, 0, 0), subjectId = 33)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX, mZ), cfg) as TimetableDayUiState.Content
        assertSame(mZ, c.blocks[0].attendance)
        assertSame(mX, c.blocks[1].attendance)
    }

    @Test
    fun `a lone cancelled lesson keeps its mark though its subject runs at another time`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
            normal(2, Time(9, 0, 0), Time(9, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
        assertNull(c.blocks[1].attendance)
    }

    @Test
    fun `a same-subject mark stays on a shifted-away and a cancelled lesson when neither ran`() {
        val mX = attendance(Time(8, 0, 0), subjectId = 11)
        val lessons = listOf(
            shiftedSource(1, Time(8, 0, 0), Time(8, 45, 0), Time(10, 0, 0), Time(10, 45, 0), subject = 11),
            cancelled(2, Time(8, 0, 0), Time(8, 45, 0), subject = 11),
        )
        val c = TimetableDayBuilder.build(date, lessons, emptyList(), listOf(mX), cfg) as TimetableDayUiState.Content
        assertSame(mX, c.blocks[0].attendance)
        assertSame(mX, c.blocks[1].attendance)
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
    fun `a lesson this session marked keeps showing its dot`() {
        // The page marks its own changes seen on arrival, which flips `seen` and makes the DAO
        // re-emit. Without `stillShowUnseen` every red dot would vanish the instant the user landed
        // on the day - so they would never learn WHICH lessons changed, which is the whole point of
        // the cue. The legacy view solved this with `Lesson.showAsUnseen`, snapshotted before
        // marking (`TimetableDayFragment.kt:257` at 98a85c41^).
        val justMarked = lesson(id = 1L, type = Lesson.TYPE_CANCELLED, seen = true)
        val longSinceSeen = lesson(id = 2L, type = Lesson.TYPE_CANCELLED, seen = true, start = Time(9, 0, 0), end = Time(9, 45, 0))
        val c = TimetableDayBuilder.build(
            date, listOf(justMarked, longSinceSeen), emptyList(), emptyList(), cfg,
            stillShowUnseen = setOf(justMarked.id),
        ) as TimetableDayUiState.Content
        assertTrue(c.blocks.first { it.lesson === justMarked }.unseen)
        assertFalse(c.blocks.first { it.lesson === longSinceSeen }.unseen)
    }

    @Test
    fun `stillShowUnseen never promotes a normal lesson`() {
        // The set is only a display override for the dot; a TYPE_NORMAL lesson has no change to
        // report and must stay dotless even if its id is in the set.
        val normal = lesson(id = 1L, type = Lesson.TYPE_NORMAL, seen = true)
        val c = TimetableDayBuilder.build(
            date, listOf(normal), emptyList(), emptyList(), cfg,
            stillShowUnseen = setOf(normal.id),
        ) as TimetableDayUiState.Content
        assertFalse(c.blocks[0].unseen)
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

    @Test
    fun `a shared slot shows each event only on the block of its subject`() {
        val lessons = listOf(
            cancelled(1, Time(8, 0, 0), Time(8, 45, 0), subject = 10),
            shiftedTarget(2, Time(10, 0, 0), Time(10, 45, 0), Time(8, 0, 0), Time(8, 45, 0), subject = 20),
        )
        val e10 = examEvent(1, Time(8, 0, 0), subject = 10)
        val e20 = examEvent(2, Time(8, 0, 0), subject = 20)
        val c = TimetableDayBuilder.build(date, lessons, listOf(e10, e20), emptyList(), cfg) as TimetableDayUiState.Content
        assertEquals(listOf(e10), c.blocks.first { it.lesson.id == 1L }.events)
        assertEquals(listOf(e20), c.blocks.first { it.lesson.id == 2L }.events)
    }

    @Test
    fun `the three-event cap counts only the block's own events`() {
        val lessons = listOf(
            normal(1, Time(8, 0, 0), Time(8, 45, 0), subject = 10),
            normal(2, Time(8, 0, 0), Time(8, 45, 0), subject = 20),
        )
        val e20 = (1L..4L).map { examEvent(it, Time(8, 0, 0), subject = 20) }
        val e10 = examEvent(5, Time(8, 0, 0), subject = 10)
        val c = TimetableDayBuilder.build(date, lessons, e20 + e10, emptyList(), cfg) as TimetableDayUiState.Content
        assertEquals(listOf(e10), c.blocks.first { it.lesson.id == 1L }.events)
        assertEquals(e20.take(3), c.blocks.first { it.lesson.id == 2L }.events)
    }
}
