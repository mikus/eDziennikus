/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.search

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SearchMatchTest {

    @Test fun `whole-value prefix is weight 1`() =
        assertEquals(1, SearchMatch.matchWeight("Matematyka", "mat"))

    @Test fun `any-word prefix is weight 2`() =
        assertEquals(2, SearchMatch.matchWeight("Zadanie domowe", "dom"))

    @Test fun `substring is weight 3`() =
        assertEquals(3, SearchMatch.matchWeight("Sprawdzian", "wdzi"))

    @Test fun `no match yields NO_MATCH`() =
        assertEquals(SearchMatch.NO_MATCH, SearchMatch.matchWeight("abc", "xyz"))

    @Test fun `case-insensitive`() =
        assertEquals(1, SearchMatch.matchWeight("Matematyka", "MAT"))

    @Test fun `diacritics folded on the value (lowercase)`() =
        assertEquals(1, SearchMatch.matchWeight("zażółć", "zazolc"))

    @Test fun `uppercase diacritic is NOT folded (legacy quirk preserved)`() =
        assertEquals(SearchMatch.NO_MATCH, SearchMatch.matchWeight("ŻABA", "zaba"))

    @Test fun `null value is NO_MATCH`() =
        assertEquals(SearchMatch.NO_MATCH, SearchMatch.matchWeight(null, "x"))

    @Test fun `relevance picks the best bucket weight`() =
        assertEquals(1, SearchMatch.relevance(listOf(listOf("Matematyka", "treść")), "mat"))

    @Test fun `relevance demotes a substring match to 100`() =
        assertEquals(100, SearchMatch.relevance(listOf(listOf("xyzabc")), "abc"))

    @Test fun `relevance is NO_MATCH when nothing matches`() =
        assertEquals(SearchMatch.NO_MATCH, SearchMatch.relevance(listOf(listOf("abc")), "zzz"))

    @Test fun `relevance skips null buckets and applies bucket-index weight`() =
        // "Fizyka" is in bucket index 1 -> 1*10 + weight 1 = 11
        assertEquals(11, SearchMatch.relevance(listOf(null, listOf(null, "Fizyka")), "fiz"))
}
