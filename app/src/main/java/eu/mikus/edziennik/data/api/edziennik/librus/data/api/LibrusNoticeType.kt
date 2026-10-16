/*
 * Copyright (c) Mikolaj Olszewski 2026-10-16.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.ext.getInt

/**
 * Maps a Librus note's `Positive` to a [Notice] type.
 *
 * `0` is a negative note, `1` a positive one and `2` a neutral one. Any other value the accessor
 * reads is [Notice.TYPE_UNKNOWN]: a missing or JSON-null `Positive`, a value it cannot coerce
 * (`true`, `"abc"`), an undocumented integer. A fractional number is not in that set: the accessor
 * truncates it (`getInt` reads `1.5` as `1`), so it maps to [Notice.TYPE_POSITIVE]. The truncation
 * belongs to the accessor and is not handled here. This used to end in `else -> NEUTRAL`, written
 * for the value 2, so a disciplinary note whose `Positive` could not be read was listed, counted
 * and notified as neutral. `else` must not be a domain value.
 *
 * The parser can neither throw on the note nor skip it: Data.saveData writes `noticeList` with
 * `clear` + `putAll` (`Data.kt:298-301`), so a throw mid-loop deletes every stored note from that
 * point on, and a skipped note is deleted with them.
 *
 * That `{0, 1, 2}` is the whole domain is not verified against a captured response. If Librus
 * omits `Positive` for a neutral note, those notes now show as unknown.
 */
internal fun librusNoticeType(note: JsonObject): Int = when (note.getInt("Positive")) {
    0 -> Notice.TYPE_NEGATIVE
    1 -> Notice.TYPE_POSITIVE
    2 -> Notice.TYPE_NEUTRAL
    else -> Notice.TYPE_UNKNOWN
}
