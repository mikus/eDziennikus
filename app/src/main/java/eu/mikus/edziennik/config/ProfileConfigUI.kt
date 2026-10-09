/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-19.
 */

package eu.mikus.edziennik.config

import eu.mikus.edziennik.data.db.entity.Profile.Companion.AGENDA_DEFAULT
import eu.mikus.edziennik.ui.home.HomeCardModel

@Suppress("RemoveExplicitTypeArguments")
class ProfileConfigUI(base: ProfileConfig) {

    var agendaViewType by base.config<Int>(AGENDA_DEFAULT)
    var agendaCompactMode by base.config<Boolean>(false)
    var agendaGroupByType by base.config<Boolean>(false)
    var agendaLessonChanges by base.config<Boolean>(true)
    var agendaTeacherAbsence by base.config<Boolean>(true)
    var agendaSubjectImportant by base.config<Boolean>(false)
    var agendaElearningMark by base.config<Boolean>(false)
    var agendaElearningGroup by base.config<Boolean>(true)

    var homeCards by base.config<List<HomeCardModel>> { listOf() }
    var homeCardsLocked by base.config<Boolean>(false)
    var homeEventsLimit by base.config<Int>(4)
    var homeEventsWeeks by base.config<Int>(4)
    var homeGradesWeeks by base.config<Int>(1)

    var messagesGreetingOnCompose by base.config<Boolean>(true)
    var messagesGreetingOnReply by base.config<Boolean>(true)
    var messagesGreetingOnForward by base.config<Boolean>(false)
    var messagesGreetingText by base.config<String?>(null)

    var timetableShowAttendance by base.config<Boolean>(true)
    var timetableShowEvents by base.config<Boolean>(true)
    var timetableTrimHourRange by base.config<Boolean>(false)
    var timetableColorSubjectName by base.config<Boolean>(false)

    companion object {
        /**
         * Ceilings for the two keys Home bakes into its queries.
         *
         * Home freezes `getNearestNotDone(..., MAX_HOME_EVENTS_LIMIT)` and a
         * `MAX_HOME_GRADES_WEEKS`-wide grades window, then narrows to the stored value in
         * HomeBuilder. That is only correct while the stored value cannot exceed the ceiling, so
         * HomeConfigDialog's sliders read these same constants as their maxima — the slider maximum
         * IS the ceiling by construction. Raising one here raises both together.
         *
         * Declared beside the keys they bound rather than on HomeBuilder: the builder consumes the
         * user's stored value and never references the ceiling.
         */
        const val MAX_HOME_EVENTS_LIMIT = 20
        const val MAX_HOME_GRADES_WEEKS = 16
    }
}
