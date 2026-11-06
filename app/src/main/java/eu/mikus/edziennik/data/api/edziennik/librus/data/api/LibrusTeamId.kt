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
 * class names a `Class` and no `VirtualClass`. In a capture of 39 events taken from the device,
 * **37 named a `Class` only, 2 a `VirtualClass` only, and none carried both**; a capture of 36
 * `Timetables` lesson entries splits the same way, 29 `Class` only / 7 `VirtualClass` only / 0
 * both. It is that last figure - never both - and not the ratio that lets `LibrusApiTimetables`
 * read the pair as `Class ?: VirtualClass` without ever preferring a whole-class id over a
 * group's. The order is kept identical here so the event side and the lesson side cannot drift
 * apart: an id that matches across them names the same group. Group events are the rare shape,
 * not the common one - replaying both readers over the captured bodies moves 2 events of 102.
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
internal fun librusEventTeamId(event: JsonObject): Long = librusTeamId(event) ?: -1

/**
 * The team a Librus object names, or null when it names none.
 *
 * `Class` and `VirtualClass` are the two ways every Librus endpoint that mentions a team names one,
 * and the capture above says an object carries one or the other and never both - so this order can
 * only ever pick the key that is there. Every parser reading the pair must read it the same way, or
 * an id stored by one will not match an id stored by another when they mean the same group.
 *
 * Null rather than a sentinel: `LibrusLesson.teamId` is a nullable column and wants null, while
 * `Event.teamId` is not, which is what [librusEventTeamId] is for.
 */
internal fun librusTeamId(json: JsonObject): Long? =
    json.getJsonObject("Class")?.getLong("Id")
        ?: json.getJsonObject("VirtualClass")?.getLong("Id")
