/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.ui.messages.compose

import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.cleanDiacritics

/**
 * Weighted recipient match: 1 = whole starts-with, 2 = any-word starts-with, 3 = contains,
 * 100 = no match. Verbatim port of the legacy MessagesComposeSuggestionAdapter.getMatchWeight —
 * diacritic-insensitive via [cleanDiacritics] (which maps only LOWERCASE Polish letters, so an
 * uppercase-accented initial does not match its unaccented query; preserved for parity).
 */
fun matchWeight(name: CharSequence?, prefix: String): Int {
    if (name == null) return 100
    val nameClean = name.cleanDiacritics()
    if (nameClean.startsWith(prefix, ignoreCase = true) || name.startsWith(prefix, ignoreCase = true)) return 1
    val words = nameClean.split(" ").toTypedArray() + name.split(" ").toTypedArray()
    for (word in words) if (word.startsWith(prefix, ignoreCase = true)) return 2
    if (nameClean.contains(prefix, ignoreCase = true) || name.contains(prefix, ignoreCase = true)) return 3
    return 100
}

/**
 * The dropdown suggestion list for [query] over [all] (which includes the synthetic type-group
 * entries, id in -24..0). Mirrors the legacy ArrayFilter: null query -> only the type-group
 * categories (the "browse categories" end-icon path); empty -> everything; else -> teachers whose
 * [matchWeight] != 100, sorted ascending by weight (stable).
 */
fun rankRecipients(all: List<Teacher>, query: String?): List<Teacher> = when {
    query == null -> all.filter { it.id in -24L..0L }
    query.isEmpty() -> all
    else -> all.filter { matchWeight(it.fullName, query) != 100 }
        .sortedBy { matchWeight(it.fullName, query) }
}
