/*
 * Copyright (c) Mikolaj Olszewski 2026-10-14.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import eu.mikus.edziennik.ext.get
import eu.mikus.edziennik.ext.getFloat

/**
 * Resolves a grade category's weight.
 *
 * A weight of `0f` already means "does not count towards the average" - both here, for a category
 * whose `CountToTheAverage` is false, and in GradesManager.getGradeWeight for a don't-count grade.
 * So it cannot also stand in for "the backend sent a Weight we could not read": GradesTreeBuilder
 * adds `value * weight` to one running total and `weight` to the other, and a substituted `0f`
 * therefore drops the whole category out of the weighted average with nothing logged or shown.
 *
 * Skipping the category instead would not help - LibrusApiGrades falls back to
 * `category?.weight ?: 0f`, so its grades would weigh 0f anyway and additionally lose the
 * category's colour and text. The throw is caught by LibrusApi's `try { onSuccess(json) }`, which
 * reports EXCEPTION_LIBRUS_API_REQUEST with the whole response attached, so the failure is loud
 * and diagnosable rather than a quietly wrong number.
 *
 * Only a Weight that is a primitive and cannot be read is a failure. An absent or JSON-null Weight
 * keeps its long-standing `0f`: it was never an error in any version, because the accessor's safe
 * call short-circuited on a missing key even before phase 68. So does a Weight that is an object
 * or an array: pre-phase-68 `getFloat` guarded on `!it.isJsonPrimitive` and gave null, and
 * `asFloatOrNull` still does, so that was equally silent in every prior version. `getFloat` returns
 * null for all four cases, which is why this takes the category rather than the already-read value.
 */
internal fun librusCategoryWeight(category: JsonObject?, countToAverage: Boolean): Float {
    if (!countToAverage)
        return 0f
    val raw = category.get("Weight")
    if (raw == null || raw.isJsonNull || !raw.isJsonPrimitive)
        return 0f
    return category.getFloat("Weight") ?: error("malformed grade category Weight")
}
