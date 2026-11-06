/*
 * Copyright (c) Mikolaj Olszewski 2026-11-6.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.ext.getJsonObject
import eu.mikus.edziennik.ext.getLong

/**
 * The team a Librus `HomeWorks` event belongs to, or `-1` when the endpoint names none.
 *
 * An event of a subject split into groups names a `VirtualClass` and no `Class`; one for the whole
 * class names a `Class` and no `VirtualClass`. A capture of 39 events taken from the device had 37
 * of the first shape, 2 of the second and **none carrying both** - the same split a capture of 36
 * lesson entries from `Timetables` shows (29 / 7 / 0), which is why `LibrusApiTimetables` can read
 * the pair as `Class ?: VirtualClass` without ever preferring a whole-class id over a group's. The
 * order is kept identical here so the event side and the lesson side cannot drift apart: an id
 * that matches across them names the same group.
 *
 * Reading `Class` alone, as this did until item 82, stored `-1` for every group event - the same
 * value as "the school named no team" - so the timetable could not tell a group's event from an
 * unattributed one and showed it on every block of its slot.
 *
 * `-1` is the "no team" value `Event.teamId` already uses; `Event.knownTeamId` reads it back as
 * null, and the timetable's `forLesson` never narrows a slot on a team it cannot name.
 *
 * This is a free function and not part of the parser's `init { apiGet { … } }` body so that it can
 * be driven from a JVM test, in the shape [librusEventDate] and [librusEventTime] already use.
 */
internal fun librusEventTeamId(event: JsonObject): Long =
    event.getJsonObject("Class")?.getLong("Id")
        ?: event.getJsonObject("VirtualClass")?.getLong("Id")
        ?: -1
