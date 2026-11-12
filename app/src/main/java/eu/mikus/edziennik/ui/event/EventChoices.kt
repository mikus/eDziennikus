/*
 * Copyright (c) Mikolaj Olszewski 2026-11-12.
 */
package eu.mikus.edziennik.ui.event

import androidx.compose.runtime.mutableStateOf
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull

/**
 * One value of [EventManualDialog]'s form that the dialog fills with a default. It is Compose state, so
 * the form reads it directly.
 */
class UserValue<T>(initial: T) {
    private val state = mutableStateOf(initial)
    private var touched = false

    val value: T get() = state.value

    /** The user set [value], directly or through something they did (a type pick resets the colour). */
    fun set(value: T) {
        state.value = value
        touched = true
    }

    /** A default: it lands only while the user has not set the value. */
    fun default(value: T) {
        if (!touched) state.value = value
    }

    /** Writes [value] without marking it as the user's. */
    internal fun overwrite(value: T) {
        state.value = value
    }
}

/**
 * The team, subject, teacher, type and colour of [EventManualDialog]'s form. The dialog loads their
 * lists before it knows their defaults, and the user can set any of them meanwhile: this, which knows
 * both a value and whether the user set it, decides whether a default still lands.
 */
class EventChoices {
    val team = UserValue<Long?>(null)
    val subject = UserValue<Long?>(null)
    val teacher = UserValue<Long?>(null)
    val type = UserValue<Long?>(null)
    val color = UserValue<Int?>(null)

    /**
     * The legacy sequence, unchanged: the team and the colour are overwritten whatever the user set
     * while the lists loaded; the subject, teacher and type only fill an empty value.
     */
    fun applyDefaults(defaults: Defaults) {
        team.overwrite(defaults.teamId)
        if (subject.value == null) subject.overwrite(defaults.subjectId)
        if (teacher.value == null) teacher.overwrite(defaults.teacherId)
        if (type.value == null) type.overwrite(defaults.typeId)
        color.overwrite(defaults.color)
    }

    /** What the form opens with, once its lists are loaded. */
    data class Defaults(
        val teamId: Long?,
        val subjectId: Long?,
        val teacherId: Long?,
        val typeId: Long?,
        val color: Int?,
    ) {
        companion object {
            /**
             * The legacy default sequence as it lands on untouched values:
             * - team: the class team; without one the edited event's team, then the lesson's (`-1` is
             *   no team, and lets the next one fill); then, when the dialog was opened from a lesson,
             *   that lesson's team regardless. So an edited event's own team never wins over a class
             *   team (inherited from upstream).
             * - subject, teacher: the edited event's, else the lesson's (null and `-1` let the next fill);
             * - type: the edited event's, else [defaultType];
             * - colour: the edited event's custom colour, if it has one.
             */
            fun of(
                classTeamId: Long?,
                editingEvent: EventFull?,
                defaultLesson: LessonFull?,
                defaultType: Long?,
            ) = Defaults(
                teamId = if (defaultLesson != null) defaultLesson.displayTeamId
                    else classTeamId ?: firstSet(editingEvent?.teamId),
                subjectId = firstSet(editingEvent?.subjectId, defaultLesson?.displaySubjectId),
                teacherId = firstSet(editingEvent?.teacherId, defaultLesson?.displayTeacherId),
                typeId = editingEvent?.type ?: defaultType,
                color = EventManualLogic.seedCustomColor(editingEvent?.color),
            )

            private fun firstSet(vararg ids: Long?): Long? = ids.firstOrNull { it != null && it != -1L }
        }
    }
}
