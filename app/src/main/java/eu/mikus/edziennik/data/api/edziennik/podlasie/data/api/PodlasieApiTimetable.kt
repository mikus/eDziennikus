/*
 * Copyright (c) Kacper Ziubryniewicz 2020-5-12
 */

package eu.mikus.edziennik.data.api.edziennik.podlasie.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.data.api.edziennik.podlasie.DataPodlasie
import eu.mikus.edziennik.data.api.models.DataRemoveModel
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getString
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.models.Week

class PodlasieApiTimetable(val data: DataPodlasie, rows: List<JsonObject>) {
    init {
        val currentWeekStart = Week.getWeekStart()

        if (Date.getToday().weekDay > 4) {
            currentWeekStart.stepForward(0, 0, 7)
        }

        val getDate = data.arguments?.getString("weekStart") ?: currentWeekStart.stringY_m_d

        val weekStart = Date.fromY_m_d(getDate)
        val weekEnd = weekStart.clone().stepForward(0, 0, 6)

        val days = mutableListOf<Int>()
        var startDate: Date? = null
        var endDate: Date? = null

        rows.forEach { lesson ->
            val date = lesson.getString("Date")?.let { Date.fromY_m_d(it) } ?: return@forEach

            if ((date > weekEnd || date < weekStart) && data.profile?.empty != true) return@forEach
            if (startDate == null) startDate = date.clone()
            endDate = date.clone()
            if (date.value !in days) days += date.value

            val lessonNumber = lesson.getInt("LessonNumber") ?: return@forEach
            val startTime = lesson.getString("TimeFrom")?.let { Time.fromH_m_s(it) }
                    ?: return@forEach
            val endTime = lesson.getString("TimeTo")?.let { Time.fromH_m_s(it) } ?: return@forEach
            val subject = lesson.getString("SchoolSubject")?.let { data.getSubject(null, it) }
                    ?: return@forEach

            val teacherFirstName = lesson.getString("TeacherFirstName") ?: return@forEach
            val teacherLastName = lesson.getString("TeacherLastName") ?: return@forEach
            val teacher = data.getTeacher(teacherFirstName, teacherLastName)

            val team = lesson.getString("Group")?.let {
                data.getTeam(
                    id = null,
                    name = it,
                    schoolCode = data.schoolShortName ?: "",
                    isTeamClass = it == "cała klasa"
                )
            } ?: return@forEach
            val classroom = lesson.getString("Room")

            Lesson(data.profileId, -1).also {
                it.type = Lesson.TYPE_NORMAL
                it.date = date
                it.lessonNumber = lessonNumber
                it.startTime = startTime
                it.endTime = endTime
                it.subjectId = subject.id
                it.teacherId = teacher.id
                it.teamId = team.id
                it.classroom = classroom

                it.id = it.buildId()
                it.ownerId = it.buildOwnerId()
                data.lessonList += it
            }
        }

        if (startDate != null && endDate != null) {
            if (weekEnd > endDate!!) endDate = weekEnd

            while (startDate!! <= endDate!!) {
                if (startDate!!.value !in days) {
                    val lessonDate = startDate!!.clone()
                    data.lessonList += Lesson(data.profileId, lessonDate.value.toLong()).apply {
                        type = Lesson.TYPE_NO_LESSONS
                        date = lessonDate
                    }
                }
                startDate!!.stepForward(0, 0, 1)
            }
        }

        data.toRemove.add(DataRemoveModel.Timetable.between(weekStart, weekEnd))
    }
}
