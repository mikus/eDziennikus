/*
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import eu.mikus.edziennik.utils.models.Time

/** Parse the "±H:MM:SS" bell-sync offset. Returns (Time, multiplier ∈ {+1,-1}) or null if invalid. */
fun bellSyncParse(input: String): Pair<Time, Int>? {
    if (input.length < 8) return null
    if (input[2] != ':' || input[5] != ':') return null
    val multiplier = when (input[0]) {
        '+' -> 1
        '-' -> -1
        else -> return null
    }
    val time = Time.fromH_m_s("0" + input.substring(1))
    return time to multiplier
}

/**
 * Faithful port of the legacy `checkForLessons`: it called `now.stepForward(+maxDiff)` then
 * `now.stepForward(-maxDiff)` on the SAME mutating Time, so the net RHS was plain `now`.
 * Effective window: `(now + maxDiffMinutes) >= first && now <= last` (asymmetric — no tail grace).
 */
fun bellSyncCanSync(times: List<Time>, now: Time, maxDiffMinutes: Int): Boolean {
    if (times.isEmpty()) return false
    val first = times.first()
    val last = times.last()
    val nowPlus = now.clone().stepForward(0, maxDiffMinutes, 0)
    return nowPlus >= first && now <= last
}

/** now↔bell diff + multiplier (-1 if the bell is still in the future, else +1). Mirrors BellSyncDialog. */
fun bellSyncActualDiff(now: Time, bellTime: Time): Pair<Time, Int> {
    val bellDiff = Time.diff(now, bellTime)
    val multiplier = if (bellTime > now) -1 else 1
    return bellDiff to multiplier
}
