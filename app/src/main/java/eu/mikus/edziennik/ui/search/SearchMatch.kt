/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.search

import eu.mikus.edziennik.ext.cleanDiacritics

/**
 * Pure, Android-free port of the legacy [SearchFilter] ranking, reusable by Compose screens.
 * Diacritic-insensitive (via [cleanDiacritics], a length-preserving 9-char lowercase-Polish fold —
 * it does NOT lowercase) AND case-insensitive (`ignoreCase = true`). 3-tier weight: whole-value
 * prefix (1) < any-word prefix (2) < substring (3); then a min-over-keyword-buckets priority. Lower
 * score = better. [NO_MATCH] = no match.
 */
object SearchMatch {
    const val NO_MATCH = 1000

    /** Weight 1 (whole-value prefix) / 2 (any-word prefix) / 3 (substring) / [NO_MATCH]. */
    fun matchWeight(name: String?, query: String): Int {
        if (name == null) return NO_MATCH
        val q = query.cleanDiacritics()
        val n = name.cleanDiacritics()
        return when {
            n.startsWith(q, ignoreCase = true) -> 1
            n.split(" ").any { it.startsWith(q, ignoreCase = true) } -> 2
            n.contains(q, ignoreCase = true) -> 3
            else -> NO_MATCH
        }
    }

    /**
     * Relevance over the [Searchable.searchKeywords] buckets: min over keywords of
     * `bucketIndex * 10 + weight`, with a substring hit (weight 3) demoted to 100. [NO_MATCH] if
     * nothing matched. Mirrors `SearchFilter.performFiltering`. Callers handle the blank-query case
     * (keep everything) themselves — do not call this with a blank query expecting "match all".
     */
    fun relevance(keywords: List<List<String?>?>, query: String): Int {
        var best = NO_MATCH
        keywords.forEachIndexed { bucketIndex, bucket ->
            bucket ?: return@forEachIndexed
            bucket.forEach { keyword ->
                var weight = matchWeight(keyword, query)
                if (weight != NO_MATCH) {
                    if (weight == 3) weight = 100
                    best = minOf(best, bucketIndex * 10 + weight)
                }
            }
        }
        return best
    }
}
