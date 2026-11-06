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
 * `-1` is the "no team" value `Event.teamId` already uses; `Event.knownTeamId` reads it back as
 * null, and the timetable's `forLesson` never narrows a slot on a team it cannot name.
 *
 * This is a free function and not part of the parser's `init { apiGet { … } }` body so that it can
 * be driven from a JVM test, in the shape [librusEventDate] and [librusEventTime] already use.
 */
internal fun librusEventTeamId(event: JsonObject): Long =
    event.getJsonObject("Class")?.getLong("Id") ?: -1
