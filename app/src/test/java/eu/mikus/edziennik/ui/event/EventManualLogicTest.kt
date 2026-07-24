/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */
package eu.mikus.edziennik.ui.event

import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventManualLogicTest {

    // 2024-01-01 is a Monday (weekDay 0); 2024-01-06 a Saturday (5); 2024-01-26 a Friday (4).

    private fun lesson(
        start: Time? = Time(8, 0, 0),
        end: Time? = Time(8, 45, 0),
        number: Int? = 1,
        subject: Long? = 10L,
        teacher: Long? = 20L,
        team: Long? = 30L,
    ): LessonFull = LessonFull(1, 100L).apply {
        startTime = start
        endTime = end
        lessonNumber = number
        subjectId = subject
        teacherId = teacher
        teamId = team
    }

    // ---- date choice list ----

    @Test fun `date list from Monday has today tomorrow three-this-week five-next-week other`() {
        val c = EventManualLogic.buildDateChoices(Date(2024, 1, 1))
        assertEquals(
            listOf(
                DateChoiceKind.TODAY,
                DateChoiceKind.TOMORROW,
                DateChoiceKind.THIS_WEEK,
                DateChoiceKind.THIS_WEEK,
                DateChoiceKind.THIS_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.OTHER,
            ),
            c.map { it.kind },
        )
        assertEquals(20240101, c[0].date!!.value)
        assertEquals(20240102, c[1].date!!.value)
        // this week: Wed/Thu/Fri
        assertEquals(20240103, c[2].date!!.value)
        assertEquals(2, c[2].weekDay)
        assertEquals(20240105, c[4].date!!.value)
        assertEquals(4, c[4].weekDay)
        // next week starts on Monday 2024-01-08
        assertEquals(20240108, c[5].date!!.value)
        assertEquals(0, c[5].weekDay)
        assertEquals(20240112, c[9].date!!.value)
        assertEquals(4, c[9].weekDay)
    }

    @Test fun `date list from Friday has no tomorrow nor this-week and crosses the month boundary`() {
        val c = EventManualLogic.buildDateChoices(Date(2024, 1, 26))
        assertEquals(
            listOf(
                DateChoiceKind.TODAY,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.OTHER,
            ),
            c.map { it.kind },
        )
        assertEquals(20240126, c[0].date!!.value)
        // next week Mon..Fri = Jan 29,30,31, Feb 1, Feb 2 (rolls into February)
        assertEquals(20240129, c[1].date!!.value)
        assertEquals(20240131, c[3].date!!.value)
        assertEquals(20240201, c[4].date!!.value)
        assertEquals(20240202, c[5].date!!.value)
    }

    @Test fun `date list from a weekend Saturday skips straight to next week`() {
        val c = EventManualLogic.buildDateChoices(Date(2024, 1, 6))
        assertEquals(
            listOf(
                DateChoiceKind.TODAY,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.NEXT_WEEK,
                DateChoiceKind.OTHER,
            ),
            c.map { it.kind },
        )
        assertEquals(20240106, c[0].date!!.value)
        assertEquals(20240108, c[1].date!!.value)
        assertEquals(20240112, c[5].date!!.value)
    }

    @Test fun `date list from Thursday has tomorrow but no this-week`() {
        // 2024-01-04 is a Thursday (weekDay 3)
        val c = EventManualLogic.buildDateChoices(Date(2024, 1, 4))
        assertEquals(DateChoiceKind.TODAY, c[0].kind)
        assertEquals(DateChoiceKind.TOMORROW, c[1].kind)
        assertEquals(DateChoiceKind.NEXT_WEEK, c[2].kind)
        assertEquals(20240105, c[1].date!!.value) // tomorrow = Friday
        assertEquals(20240108, c[2].date!!.value) // next week Monday
    }

    @Test fun `date list does not mutate the input date`() {
        val today = Date(2024, 1, 1)
        EventManualLogic.buildDateChoices(today)
        assertEquals(20240101, today.value)
    }

    @Test fun `next-lesson row is prepended only when a subject is given`() {
        val without = EventManualLogic.buildDateChoices(Date(2024, 1, 1))
        assertTrue(without.none { it.kind == DateChoiceKind.NEXT_LESSON })

        val with = EventManualLogic.buildDateChoices(Date(2024, 1, 1), 42L, "Matematyka")
        val head = with.first()
        assertEquals(DateChoiceKind.NEXT_LESSON, head.kind)
        assertEquals(42L, head.subjectId)
        assertEquals("Matematyka", head.subjectName)
        assertNull(head.date)
        assertEquals(DateChoiceKind.TODAY, with[1].kind)
    }

    // ---- time selection model ----

    @Test fun `all-day maps to null event time and is valid`() {
        assertNull(TimeSelection.AllDay.toEventTime())
        assertTrue(TimeSelection.AllDay.isValid)
    }

    @Test fun `custom maps to its start time and is valid`() {
        val sel = TimeSelection.Custom(Time(9, 15, 0), Time(10, 0, 0))
        assertEquals(Time(9, 15, 0), sel.toEventTime())
        assertTrue(sel.isValid)
    }

    @Test fun `lesson maps to its start time and is valid`() {
        val sel = TimeSelection.Lesson(Time(8, 0, 0), Time(8, 45, 0), 1, 10L, 20L, 30L)
        assertEquals(Time(8, 0, 0), sel.toEventTime())
        assertTrue(sel.isValid)
    }

    @Test fun `none maps to null and is invalid`() {
        assertNull(TimeSelection.None.toEventTime())
        assertFalse(TimeSelection.None.isValid)
    }

    @Test fun `lessonToTimeSelection carries the cascade ids`() {
        val sel = EventManualLogic.lessonToTimeSelection(lesson()) as TimeSelection.Lesson
        assertEquals(Time(8, 0, 0), sel.startTime)
        assertEquals(Time(8, 45, 0), sel.endTime)
        assertEquals(1, sel.lessonNumber)
        assertEquals(10L, sel.subjectId)
        assertEquals(20L, sel.teacherId)
        assertEquals(30L, sel.teamId)
    }

    @Test fun `lessonToTimeSelection with no start time is None`() {
        assertEquals(TimeSelection.None, EventManualLogic.lessonToTimeSelection(lesson(start = null)))
    }

    // ---- cascade decision ----

    @Test fun `cascade for a lesson carries all display ids`() {
        val c = EventManualLogic.cascadeFor(lesson())
        assertEquals(10L, c.subjectId)
        assertEquals(20L, c.teacherId)
        assertEquals(30L, c.teamId)
    }

    @Test fun `cascade for a lesson with missing ids yields nulls (deselect and class fallback)`() {
        val c = EventManualLogic.cascadeFor(lesson(subject = null, teacher = null, team = null))
        assertNull(c.subjectId)
        assertNull(c.teacherId)
        assertNull(c.teamId)
    }

    @Test fun `cascade for a null lesson (plain date pick) yields all nulls`() {
        val c = EventManualLogic.cascadeFor(null)
        assertNull(c.subjectId)
        assertNull(c.teacherId)
        assertNull(c.teamId)
    }

    // ---- validation ----

    @Test fun `validate passes with date, time, type and topic`() {
        val r = EventManualLogic.validate(
            date = Date(2024, 1, 1),
            time = TimeSelection.Custom(Time(9, 0, 0)),
            typeId = Event.TYPE_EXAM,
            topic = "Sprawdzian",
        )
        assertTrue(r.isValid)
        assertFalse(r.dateInvalid || r.timeInvalid || r.typeInvalid || r.topicInvalid)
    }

    @Test fun `validate flags missing date`() {
        val r = EventManualLogic.validate(null, TimeSelection.AllDay, Event.TYPE_EXAM, "x")
        assertTrue(r.dateInvalid)
        assertFalse(r.isValid)
    }

    @Test fun `validate flags invalid time (None)`() {
        val r = EventManualLogic.validate(Date(2024, 1, 1), TimeSelection.None, Event.TYPE_EXAM, "x")
        assertTrue(r.timeInvalid)
        assertFalse(r.isValid)
    }

    @Test fun `validate accepts all-day as a valid time`() {
        val r = EventManualLogic.validate(Date(2024, 1, 1), TimeSelection.AllDay, Event.TYPE_EXAM, "x")
        assertFalse(r.timeInvalid)
        assertTrue(r.isValid)
    }

    @Test fun `validate flags missing type`() {
        val r = EventManualLogic.validate(Date(2024, 1, 1), TimeSelection.AllDay, null, "x")
        assertTrue(r.typeInvalid)
        assertFalse(r.isValid)
    }

    @Test fun `validate flags null topic`() {
        val r = EventManualLogic.validate(Date(2024, 1, 1), TimeSelection.AllDay, Event.TYPE_EXAM, null)
        assertTrue(r.topicInvalid)
        assertFalse(r.isValid)
    }

    @Test fun `validate flags blank topic`() {
        val r = EventManualLogic.validate(Date(2024, 1, 1), TimeSelection.AllDay, Event.TYPE_EXAM, "   ")
        assertTrue(r.topicInvalid)
        assertFalse(r.isValid)
    }

    @Test fun `validate reports every flag at once`() {
        val r = EventManualLogic.validate(null, TimeSelection.None, null, "")
        assertTrue(r.dateInvalid && r.timeInvalid && r.typeInvalid && r.topicInvalid)
        assertFalse(r.isValid)
    }

    // ---- color rules ----

    @Test fun `swatch prefers the custom color`() {
        assertEquals(0x11223344, EventManualLogic.swatchColor(0x11223344, Event.COLOR_EXAM))
    }

    @Test fun `swatch falls back to the type color`() {
        assertEquals(Event.COLOR_EXAM, EventManualLogic.swatchColor(null, Event.COLOR_EXAM))
    }

    @Test fun `swatch falls back to the default color`() {
        assertEquals(Event.COLOR_DEFAULT, EventManualLogic.swatchColor(null, null))
    }

    @Test fun `seed custom color treats -1 sentinel and null as no color`() {
        assertNull(EventManualLogic.seedCustomColor(-1))
        assertNull(EventManualLogic.seedCustomColor(null))
    }

    @Test fun `seed custom color keeps a real stored color`() {
        assertEquals(Event.COLOR_ESSAY, EventManualLogic.seedCustomColor(Event.COLOR_ESSAY))
    }

    @Test fun `changing the type clears the custom color`() {
        assertNull(EventManualLogic.customColorAfterTypeChange())
    }
}
