/*
 * Copyright (c) Mikolaj Olszewski 2026-11-12.
 */
package eu.mikus.edziennik.ui.event

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ui.event.EventChoices.Defaults
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EventChoicesTest {

    private val classTeam = 100L
    private val groupTeam = 200L

    private fun event(
        teamId: Long = -1L,
        subjectId: Long = -1L,
        teacherId: Long = -1L,
        type: Long = 1L,
        color: Int? = null,
    ) = EventFull(1, 1L, Date(2026, 11, 16), Time(8, 0, 0), "t", color, type, teacherId, subjectId, teamId)

    private fun lesson(teamId: Long? = 300L, subjectId: Long? = 10L, teacherId: Long? = 20L): LessonFull =
        LessonFull(1, 2L).apply {
            type = Lesson.TYPE_NORMAL
            this.teamId = teamId
            this.subjectId = subjectId
            this.teacherId = teacherId
        }

    // ---- characterisation: the legacy default sequence (EventManualDialog.loadInitial at 8d32dfb4) ----

    @Test fun `an Add with nothing to open from defaults to the class team and the given type`() {
        assertEquals(Defaults(classTeam, null, null, 3L, null), Defaults.of(classTeam, null, null, 3L))
    }

    @Test fun `without a class team an edited event's own team is the default`() {
        assertEquals(groupTeam, Defaults.of(null, event(teamId = groupTeam), null, null).teamId)
    }

    @Test fun `an edited event's own team loses to the class team`() {
        // N6: inherited from upstream (selectTeamClass, then a selectDefault that never fills).
        assertEquals(classTeam, Defaults.of(classTeam, event(teamId = groupTeam), null, null).teamId)
    }

    @Test fun `an edited event with no team and no class team defaults to no team`() {
        assertEquals(null, Defaults.of(null, event(teamId = -1L), null, null).teamId)
    }

    @Test fun `an Add-from-lesson dialog's lesson team wins over the class team`() {
        assertEquals(300L, Defaults.of(classTeam, null, lesson(teamId = 300L), null).teamId)
    }

    @Test fun `a lesson with no team overrides the class team with no team`() {
        assertEquals(null, Defaults.of(classTeam, null, lesson(teamId = null), null).teamId)
    }

    @Test fun `an edited event's subject and teacher win over the lesson's`() {
        val d = Defaults.of(null, event(subjectId = 11L, teacherId = 21L), lesson(subjectId = 10L, teacherId = 20L), null)
        assertEquals(11L to 21L, d.subjectId to d.teacherId)
    }

    @Test fun `an edited event with no subject or teacher lets the lesson's fill them`() {
        val d = Defaults.of(null, event(subjectId = -1L, teacherId = -1L), lesson(subjectId = 10L, teacherId = 20L), null)
        assertEquals(10L to 20L, d.subjectId to d.teacherId)
    }

    @Test fun `a lesson's no subject or teacher defaults to none`() {
        val d = Defaults.of(null, null, lesson(subjectId = -1L, teacherId = null), null)
        assertEquals(null to null, d.subjectId to d.teacherId)
    }

    @Test fun `an edited event's type wins over the given type`() {
        assertEquals(7L, Defaults.of(null, event(type = 7L), null, 3L).typeId)
    }

    @Test fun `an edited event's custom colour is the default, and its no colour is none`() {
        assertEquals(0x123456, Defaults.of(null, event(color = 0x123456), null, null).color)
        assertEquals(null, Defaults.of(null, event(color = -1), null, null).color)
    }

    // ---- applying the defaults ----

    private val defaults = Defaults(classTeam, 10L, 20L, 3L, 0x123456)

    @Test fun `untouched values all take their defaults`() {
        val choices = EventChoices().apply { applyDefaults(defaults) }
        assertEquals(
            listOf<Any?>(classTeam, 10L, 20L, 3L, 0x123456),
            with(choices) { listOf(team.value, subject.value, teacher.value, type.value, color.value) },
        )
    }

    @Test fun `a subject the user picked during the load is kept`() {
        val choices = EventChoices().apply { subject.set(11L); applyDefaults(defaults) }
        assertEquals(11L, choices.subject.value)
    }

    @Test fun `a type the user picked during the load is kept`() {
        val choices = EventChoices().apply { type.set(4L); applyDefaults(defaults) }
        assertEquals(4L, choices.type.value)
    }

    // ---- item 94: a default never overwrites a value the user set while the lists loaded ----

    @Test fun `a team the user picked during the load survives the class-team default`() {
        val choices = EventChoices().apply {
            team.set(groupTeam)
            applyDefaults(Defaults.of(classTeam, null, null, null))
        }
        assertEquals(groupTeam, choices.team.value)
    }

    @Test fun `a team the user picked during the load survives an Add-from-lesson dialog's lesson team`() {
        val choices = EventChoices().apply {
            team.set(groupTeam)
            applyDefaults(Defaults.of(classTeam, null, lesson(teamId = 300L), null))
        }
        assertEquals(groupTeam, choices.team.value)
    }

    @Test fun `a colour the user picked during the load survives the edited event's colour`() {
        val choices = EventChoices().apply { color.set(0xABCDEF); applyDefaults(defaults) }
        assertEquals(0xABCDEF, choices.color.value)
    }

    @Test fun `a type picked during the load keeps its colour reset over the edited event's colour`() {
        // onTypeSelect sets the type and resets the colour, so the swatch follows the picked type.
        val choices = EventChoices().apply { type.set(4L); color.set(null); applyDefaults(defaults) }
        assertEquals(4L to null, choices.type.value to choices.color.value)
    }

    @Test fun `no subject picked during the load is not refilled`() {
        val choices = EventChoices().apply { subject.set(null); applyDefaults(defaults) }
        assertEquals(null, choices.subject.value)
    }

    @Test fun `no teacher picked during the load is not refilled`() {
        val choices = EventChoices().apply { teacher.set(null); applyDefaults(defaults) }
        assertEquals(null, choices.teacher.value)
    }
}
