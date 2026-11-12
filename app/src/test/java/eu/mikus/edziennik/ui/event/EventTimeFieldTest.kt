/*
 * Copyright (c) Mikolaj Olszewski 2026-11-12.
 */
package eu.mikus.edziennik.ui.event

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ui.dialogs.base.FormDropdownItem
import eu.mikus.edziennik.ui.event.EventTimeField.Opening
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventTimeFieldTest {

    private val monday = Date(2026, 11, 16)
    private val tuesday = Date(2026, 11, 17)

    private val labels = EventTimeItems.Labels(
        allDay = "All day",
        customTime = "Custom…",
        noTimetable = "No timetable",
        noLessons = "No lessons",
    )

    private fun lesson(
        id: Long,
        start: Time? = Time(8, 0, 0),
        subject: String? = "Maths",
        subjectId: Long? = 10L,
        end: Time? = null,
    ): LessonFull = LessonFull(1, id).apply {
        type = Lesson.TYPE_NORMAL
        startTime = start
        endTime = end
        subjectName = subject
        this.subjectId = subjectId
    }

    private fun event(time: Time?, subjectId: Long = -1L) =
        EventFull(1, 1L, Date(2026, 11, 16), time, "t", null, 1L, -1L, subjectId, -1L)

    private fun rows(vararg lessons: LessonFull) = EventTimeItems.build(lessons.toList(), labels)

    private val EventTimeField.selectedRow get() = items.firstOrNull { it.id == selectedId }
    private val EventTimeField.customRows get() = items.filter { it.tag is TimeSelection.Custom }

    /** Every install in this class goes through here, so every successful one is held to I1–I3. */
    private fun EventTimeField.checkedInstall(ticket: EventTimeField.Ticket, rows: List<FormDropdownItem>) =
        install(ticket, rows).also { if (it) assertInvariants(this, rows) }

    /** Opens the field the way loadInitial does: open it on its date, then install the first rows. */
    private fun opened(opening: Opening, first: List<FormDropdownItem>) =
        EventTimeField(opening).also { field -> assertTrue(field.checkedInstall(field.open(monday), first)) }

    /** A sync rebuild: take the ticket, install the rebuilt rows. */
    private fun EventTimeField.sync(rebuilt: List<FormDropdownItem>) = checkedInstall(syncTicket(), rebuilt)

    /** The ticket a sync takes: the field has a date by then. */
    private fun EventTimeField.syncTicket() = assertNotNull(ticket())

    /**
     * I1: the selection is a row of the list (or nothing). I2: ids are unique. I3: beside the [built]
     * rows there is at most one custom row, and it is the selected one.
     */
    private fun assertInvariants(field: EventTimeField, built: List<FormDropdownItem>) {
        field.selectedId?.let { id -> assertTrue(field.items.any { it.id == id }, "selected $id is in no row") }
        assertEquals(field.items.size, field.items.map { it.id }.toSet().size, "ids are not unique")
        val extra = field.items.filter { row -> built.none { it.id == row.id } }
        assertTrue(extra.size <= 1, "more than one row beside the built ones: $extra")
        extra.singleOrNull()?.let {
            assertTrue(it.tag is TimeSelection.Custom, "an extra row that is not a custom time: $it")
            assertEquals(field.selectedId, it.id, "an extra custom row that is not selected")
        }
    }

    // Two lessons in one slot (a group split): only the subject tells them apart.
    private val english = lesson(1L, Time(8, 0, 0), "English", subjectId = 11L)
    private val maths = lesson(2L, Time(8, 0, 0), "Maths", subjectId = 10L)
    private val physics = lesson(3L, Time(10, 0, 0), "Physics", subjectId = 12L)

    // ---- the opening time ----

    @Test fun `opening of an edited event is its time and subject`() {
        assertEquals(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), Opening.of(event(Time(8, 0, 0), 10L), null))
    }

    @Test fun `opening of an edited event with no time is all day`() {
        assertSame(Opening.AllDay, Opening.of(event(null), null))
    }

    @Test fun `opening of a lesson is its start, id and subject`() {
        assertEquals(Opening.At(Time(8, 0, 0), 2L, 10L), Opening.of(null, maths))
    }

    @Test fun `opening of a lesson with no start is none`() {
        assertSame(Opening.None, Opening.of(null, lesson(5L, start = null)))
    }

    @Test fun `no edited event and no lesson opens on none`() {
        assertSame(Opening.None, Opening.of(null, null))
    }

    @Test fun `an edited event wins over a lesson`() {
        assertEquals(Opening.At(Time(10, 0, 0), preferSubjectId = 12L), Opening.of(event(Time(10, 0, 0), 12L), maths))
        assertSame(Opening.AllDay, Opening.of(event(null), maths))
    }

    // ---- characterisation: the first install, and what a sync keeps ----

    @Test fun `first install selects the opening subject's lesson among two at the same start`() {
        val built = rows(english, maths)
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), built)

        assertSame(maths, field.selectedRow?.tag)
        assertInvariants(field, built)
    }

    @Test fun `first install of an edited all-day event selects all day`() {
        val built = rows(maths)
        val field = opened(Opening.AllDay, built)

        assertEquals(EventTimeItems.ALL_DAY_ID, field.selectedId)
        assertInvariants(field, built)
    }

    @Test fun `an edited all-day event stays all day after a sync`() {
        val field = opened(Opening.AllDay, rows())
        val rebuilt = rows(maths)
        assertTrue(field.sync(rebuilt))

        assertEquals(EventTimeItems.ALL_DAY_ID, field.selectedId)
        assertInvariants(field, rebuilt)
    }

    @Test fun `first install with no lesson at the opening time selects a custom row for it`() {
        val built = rows()
        val field = opened(Opening.At(Time(8, 0, 0)), built)

        assertEquals(TimeSelection.Custom(Time(8, 0, 0)), field.selectedRow?.tag)
        assertInvariants(field, built)
    }

    @Test fun `with no opening time nothing is selected after the first install`() {
        val built = rows(maths)
        val field = opened(Opening.None, built)

        assertNull(field.selectedId)
        assertEquals(built, field.items)
    }

    @Test fun `untouched field re-applies the opening subject's lesson after a sync`() {
        // Opened on a day with no timetable: the opening time is a custom row until the sync lands.
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows())
        val rebuilt = rows(english, maths)
        assertTrue(field.sync(rebuilt))

        assertSame(maths, field.selectedRow?.tag)
        assertEquals(emptyList(), field.customRows)
        assertInvariants(field, rebuilt)
    }

    @Test fun `a held lesson re-keyed by a sync is found again by start and subject`() {
        val field = opened(Opening.None, rows(english, maths))
        field.pick(maths.id)
        val rekeyed = lesson(20L, Time(8, 0, 0), "Maths", subjectId = 10L)
        val rebuilt = rows(english, rekeyed)
        assertTrue(field.sync(rebuilt))

        assertSame(rekeyed, field.selectedRow?.tag)
        assertInvariants(field, rebuilt)
    }

    @Test fun `a row picked during a sync build is the one re-found`() {
        val field = opened(Opening.None, rows(maths, physics))
        field.pick(maths.id)
        val ticket = field.syncTicket()
        field.pick(physics.id) // while the rows are being rebuilt
        val rekeyed = lesson(30L, Time(10, 0, 0), "Physics", subjectId = 12L)
        val rebuilt = rows(maths, rekeyed)
        assertTrue(field.checkedInstall(ticket, rebuilt))

        assertSame(rekeyed, field.selectedRow?.tag)
        assertInvariants(field, rebuilt)
    }

    @Test fun `a sync ticket taken before a date choice installs nothing`() {
        val field = opened(Opening.None, rows(maths))
        val stale = field.syncTicket()
        val date = field.chooseDate(tuesday, null)
        val chosen = rows(physics)
        assertTrue(field.checkedInstall(date, chosen))
        field.pick(physics.id)

        assertFalse(field.checkedInstall(stale, rows(english, maths)))
        assertEquals(chosen, field.items)
        assertEquals(physics.id, field.selectedId)
    }

    @Test fun `a plain date choice clears the time`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows(maths))
        val ticket = field.chooseDate(tuesday, null)
        assertNull(field.selectedId)
        val chosen = rows(english, physics)
        assertTrue(field.checkedInstall(ticket, chosen))

        assertNull(field.selectedId)
        assertEquals(chosen, field.items)
    }

    @Test fun `a next-lesson date choice selects that lesson once its rows arrive`() {
        val field = opened(Opening.None, rows(physics))
        val ticket = field.chooseDate(tuesday, maths)
        val chosen = rows(english, maths)
        assertTrue(field.checkedInstall(ticket, chosen))

        assertSame(maths, field.selectedRow?.tag)
        assertInvariants(field, chosen)
    }

    @Test fun `a custom pick at a lesson's start selects the lesson row`() {
        val field = opened(Opening.None, rows(physics))
        field.pickCustom(Time(10, 0, 0))

        assertSame(physics, field.selectedRow?.tag)
        assertEquals(emptyList(), field.customRows)
    }

    // ---- item 89 + 90b: the opening time until the user touches time or date, then theirs ----

    @Test fun `a sync after the user picks another lesson keeps their lesson`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows(maths, physics))
        field.pick(physics.id)
        val rebuilt = rows(maths, physics)
        assertTrue(field.sync(rebuilt))

        assertSame(physics, field.selectedRow?.tag)
        assertInvariants(field, rebuilt)
    }

    @Test fun `a sync after the user picks another lesson of an Add-from-lesson dialog keeps their lesson`() {
        val field = opened(Opening.of(null, maths), rows(maths, physics))
        assertSame(maths, field.selectedRow?.tag)
        field.pick(physics.id)
        val rebuilt = rows(maths, physics)
        assertTrue(field.sync(rebuilt))

        assertSame(physics, field.selectedRow?.tag)
        assertInvariants(field, rebuilt)
    }

    @Test fun `a sync after a custom pick keeps it as the only custom row`() {
        // Edited at 8:00 on a day with no 8:00 lesson, so the opening time is itself a custom row.
        val field = opened(Opening.At(Time(8, 0, 0)), rows(physics))
        field.pickCustom(Time(10, 15, 0))
        val rebuilt = rows(physics)
        assertTrue(field.sync(rebuilt))

        assertEquals(TimeSelection.Custom(Time(10, 15, 0)), field.selectedRow?.tag)
        assertEquals(listOf(TimeSelection.Custom(Time(10, 15, 0))), field.customRows.map { it.tag })
        assertInvariants(field, rebuilt)
    }

    @Test fun `a sync after a date choice leaves the time empty`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows(maths))
        val chosen = rows(english, maths)
        assertTrue(field.checkedInstall(field.chooseDate(tuesday, null), chosen))
        assertTrue(field.sync(chosen))

        assertNull(field.selectedId)
        assertEquals(chosen, field.items)
    }

    @Test fun `a sync after tapping no timetable leaves the time empty`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows())
        field.pick(null) // "no timetable" clears the field
        val rebuilt = rows(maths)
        assertTrue(field.sync(rebuilt))

        assertNull(field.selectedId)
        assertEquals(rebuilt, field.items)
    }

    @Test fun `a pick after a sync showed the chosen date's rows survives that date's own late install`() {
        val field = opened(Opening.None, rows(english))
        val dateTicket = field.chooseDate(tuesday, maths)
        val chosen = rows(maths, physics)
        assertTrue(field.sync(chosen)) // a sync finishes first and shows the chosen date's rows
        field.pick(physics.id)
        assertTrue(field.checkedInstall(dateTicket, chosen)) // the date's own build lands last

        assertSame(physics, field.selectedRow?.tag)
        assertInvariants(field, chosen)
    }

    // ---- item 90a: nothing from a superseded date choice reaches the field ----

    @Test fun `a date reload superseded by a later date choice installs nothing`() {
        val field = opened(Opening.None, rows(english))
        val first = field.chooseDate(tuesday, maths)
        val second = field.chooseDate(tuesday, physics)
        val before = field.items

        assertFalse(field.checkedInstall(first, rows(english, maths)))
        assertEquals(before, field.items)
        assertNull(field.selectedId)

        val chosen = rows(physics)
        assertTrue(field.checkedInstall(second, chosen))
        assertSame(physics, field.selectedRow?.tag)
        assertInvariants(field, chosen)
    }

    @Test fun `the opening build superseded by a date choice installs nothing`() {
        val field = EventTimeField(Opening.At(Time(8, 0, 0), preferSubjectId = 10L))
        val opening = field.open(monday)
        val date = field.chooseDate(tuesday, null)

        assertFalse(field.checkedInstall(opening, rows(maths)))
        assertEquals(emptyList(), field.items)
        assertNull(field.selectedId)

        val chosen = rows(english, physics)
        assertTrue(field.checkedInstall(date, chosen))
        assertNull(field.selectedId)
        assertEquals(chosen, field.items)
    }

    @Test fun `a date choice empties the rows until its own arrive`() {
        // Otherwise a lesson of the old date could be picked meanwhile: its id is in no row of the new
        // date (Lesson.buildId hashes the date), and its cascade would fight the date choice's own.
        val field = opened(Opening.None, rows(maths, physics))
        field.chooseDate(tuesday, null)

        assertEquals(emptyList(), field.items)
        assertNull(field.selectedId)
    }

    @Test fun `a sync ticket taken after a date choice installs`() {
        val field = opened(Opening.None, rows(english))
        field.chooseDate(tuesday, maths)
        val chosen = rows(maths, physics)
        assertTrue(field.sync(chosen))

        assertSame(maths, field.selectedRow?.tag)
        assertInvariants(field, chosen)
    }

    @Test fun `a ticket stops being current when a date is chosen`() {
        // What the next-lesson lookup checks before it applies the lesson it found.
        val field = opened(Opening.None, rows(maths))
        val lookup = field.syncTicket()
        assertTrue(field.isCurrent(lookup))

        val date = field.chooseDate(tuesday, null)
        assertFalse(field.isCurrent(lookup))
        assertTrue(field.isCurrent(date))
        assertTrue(field.isCurrent(field.syncTicket()))
    }

    // ---- pinned after the branch review: lines whose mutants survived every case above ----

    // A group split of one subject: only the lesson id tells the two rows apart.
    private val groupA = lesson(41L, Time(8, 0, 0), "Maths", subjectId = 10L)
    private val groupB = lesson(42L, Time(8, 0, 0), "Maths", subjectId = 10L)

    @Test fun `a sync after a next-lesson choice keeps that lesson, not the opening time`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows(maths, physics))
        val chosen = rows(maths, physics)
        assertTrue(field.checkedInstall(field.chooseDate(tuesday, physics), chosen))
        assertSame(physics, field.selectedRow?.tag)
        assertTrue(field.sync(chosen))

        assertSame(physics, field.selectedRow?.tag)
    }

    @Test fun `a picked lesson that a sync removes leaves the time empty`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows(maths, physics))
        field.pick(physics.id)
        val rebuilt = rows(maths) // the 10:00 lesson is gone
        assertTrue(field.sync(rebuilt))

        assertNull(field.selectedId)
        assertEquals(rebuilt, field.items)
    }

    @Test fun `an Add-from-lesson dialog selects that very lesson among two of its subject at the same start`() {
        val field = opened(Opening.of(null, groupB), rows(groupA, groupB))

        assertSame(groupB, field.selectedRow?.tag)
    }

    @Test fun `a next-lesson choice selects that very lesson among two of its subject at the same start`() {
        val field = opened(Opening.None, rows(english))
        assertTrue(field.checkedInstall(field.chooseDate(tuesday, groupB), rows(groupA, groupB)))

        assertSame(groupB, field.selectedRow?.tag)
    }

    @Test fun `a next-lesson choice whose lesson has no start leaves the time empty`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows(maths))
        assertTrue(field.checkedInstall(field.chooseDate(tuesday, lesson(9L, start = null)), rows(maths, physics)))

        assertNull(field.selectedId)
    }

    // ---- pinned after the orchestrator's review: two lines the branch review's mutants missed ----

    @Test fun `a next-lesson lesson re-keyed before its rows arrive is found by start and subject`() {
        val field = opened(Opening.None, rows(english))
        val rekeyed = lesson(50L, Time(8, 0, 0), "Maths", subjectId = 10L)
        val chosen = rows(english, rekeyed)
        assertTrue(field.checkedInstall(field.chooseDate(tuesday, maths), chosen))

        assertSame(rekeyed, field.selectedRow?.tag)
        assertInvariants(field, chosen)
    }

    @Test fun `a next-lesson lesson a later sync removes leaves the time empty, not a custom row`() {
        val field = opened(Opening.At(Time(8, 0, 0), preferSubjectId = 10L), rows(maths))
        assertTrue(field.checkedInstall(field.chooseDate(tuesday, physics), rows(maths, physics)))
        assertSame(physics, field.selectedRow?.tag)
        val rebuilt = rows(maths) // the 10:00 lesson is gone
        assertTrue(field.sync(rebuilt))

        assertNull(field.selectedId)
        assertEquals(emptyList(), field.customRows)
        assertEquals(rebuilt, field.items)
    }

    // ---- resolved(): what Save stores ----

    @Test fun `resolved is none when nothing is selected`() {
        assertSame(TimeSelection.None, opened(Opening.None, rows(maths)).resolved())
    }

    @Test fun `resolved is all day`() {
        assertSame(TimeSelection.AllDay, opened(Opening.AllDay, rows(maths)).resolved())
    }

    @Test fun `resolved is a lesson's start and end`() {
        val withEnd = lesson(7L, Time(8, 0, 0), end = Time(8, 45, 0))
        val field = opened(Opening.None, rows(withEnd))
        field.pick(withEnd.id)

        assertEquals(EventManualLogic.lessonToTimeSelection(withEnd), field.resolved())
        assertEquals(Time(8, 45, 0), (field.resolved() as TimeSelection.Lesson).endTime)
    }

    @Test fun `resolved is a custom time`() {
        val field = opened(Opening.None, rows(maths))
        field.pickCustom(Time(14, 30, 0))

        assertEquals(TimeSelection.Custom(Time(14, 30, 0)), field.resolved())
    }

    @Test fun `resolved is none when the selected id is in no row`() {
        val field = opened(Opening.None, rows(maths))
        field.pick(999L)

        assertSame(TimeSelection.None, field.resolved())
    }

    // ---- item 91: the rows belong to the date the field is for ----

    @Test fun `a field the dialog has not opened on a date issues no ticket`() {
        // A sync before loadInitial opens the field would otherwise build for a guessed date and race
        // the opening build at the same generation, the last install winning.
        assertNull(EventTimeField(Opening.None).ticket())
    }

    @Test fun `the opening ticket is for the date the dialog opened on`() {
        val field = EventTimeField(Opening.None)
        assertEquals(monday, field.open(monday).date)
        assertEquals(monday, field.date)
    }

    @Test fun `a sync ticket is for the opening date when no date was chosen`() {
        // Homework -> Add: the Date field stays empty, but the rows are the opening date's, and a sync
        // (the dialog's own timetable sync included) must rebuild them.
        val field = opened(Opening.None, rows(maths))
        assertEquals(monday, field.syncTicket().date)
        assertTrue(field.sync(rows(maths, physics)))
    }

    @Test fun `a date choice's ticket is for that date`() {
        val field = opened(Opening.None, rows(maths))
        assertEquals(tuesday, field.chooseDate(tuesday, null).date)
        assertEquals(tuesday, field.date)
    }

    @Test fun `a sync ticket after a date choice is for the chosen date`() {
        val field = opened(Opening.None, rows(maths))
        field.chooseDate(tuesday, maths)
        assertEquals(tuesday, field.syncTicket().date)
    }
}
