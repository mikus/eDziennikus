/*
 * Copyright (c) Mikolaj Olszewski 2026-11-6.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.LibrusLesson
import eu.mikus.edziennik.ext.getJsonObject
import eu.mikus.edziennik.ext.getLong

/**
 * The `librusLessons` row a `/2.0/Lessons` entry stands for, or null when the entry is unusable.
 *
 * A Lessons entry is a teaching assignment - a teacher teaching a subject to a team - and the row
 * is what `LibrusApiAttendances` later looks a mark's teacher and subject up in. An entry missing
 * its own id, its teacher or its subject answers null and is dropped, which is what the parser's
 * three `?: return@forEach` guards did before this was a function: two of the three fields the
 * lookup exists to supply would be absent, so the row would be a lookup that answers nothing.
 *
 * A missing team is not such a case. `LibrusLesson.teamId` is nullable and the row is still a
 * usable teacher/subject lookup without it, so an entry naming no team is kept.
 *
 * The team goes through [librusTeamId], so a group's assignment is stored under the group and not
 * under null: `/2.0/Lessons` names a group with `VirtualClass` and gives it no `Class`, the same
 * way `/2.0/Timetables` and `/2.0/HomeWorks` do, and reading `Class` alone left `teamId` null for
 * exactly the assignments a team is wanted for.
 *
 * This is a free function and not part of the parser's `init { apiGet { … } }` body so that it can
 * be driven from a JVM test, in the shape [librusEventDate] and [librusEvent] already use.
 */
internal fun librusLessonRow(profileId: Int, lesson: JsonObject): LibrusLesson? {
    val id = lesson.getLong("Id") ?: return null
    val teacherId = lesson.getJsonObject("Teacher")?.getLong("Id") ?: return null
    val subjectId = lesson.getJsonObject("Subject")?.getLong("Id") ?: return null
    return LibrusLesson(profileId, id, teacherId, subjectId, librusTeamId(lesson))
}
