/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.ui.messages.compose

import eu.mikus.edziennik.data.db.entity.Teacher
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RecipientRankingTest {
    @Test fun `whole starts-with is weight 1`() = assertEquals(1, matchWeight("Anna Kowalska", "Anna"))
    @Test fun `any-word starts-with is weight 2`() = assertEquals(2, matchWeight("Anna Kowalska", "Kowal"))
    @Test fun `contains is weight 3`() = assertEquals(3, matchWeight("Anna Kowalska", "owal"))
    @Test fun `no match is weight 100`() = assertEquals(100, matchWeight("Anna Kowalska", "xyz"))
    @Test fun `case-insensitive`() = assertEquals(1, matchWeight("Anna", "anna"))
    @Test fun `null name is 100`() = assertEquals(100, matchWeight(null, "a"))

    // cleanDiacritics maps ONLY lowercase Polish letters (ż ó ł ć ę ś ą ź ń) — uppercase Ł/Ż pass
    // through unchanged, so these use lowercase-accented names (asserting the REAL behavior):
    @Test fun `diacritic-insensitive query matches lowercase accented name`() =
        assertEquals(1, matchWeight("łukasz żółć", "lukasz"))
    @Test fun `contains via cleaned lowercase diacritic`() =
        assertEquals(3, matchWeight("żółć", "olc"))

    private val category = Teacher(1, 0L, "", "")
    private val category2 = Teacher(1, -24L, "", "")
    private val kowalska = Teacher(1, 100L, "Anna", "Kowalska")
    private val kowal = Teacher(1, 101L, "Kowal", "Nowak")
    private val nowak = Teacher(1, 102L, "Jan", "Nowak")
    private val all = listOf(category, category2, kowalska, kowal, nowak)

    @Test fun `null query yields only type-group categories`() =
        assertEquals(listOf(0L, -24L), rankRecipients(all, null).map { it.id })

    @Test fun `empty query yields everything`() =
        assertEquals(all.map { it.id }, rankRecipients(all, "").map { it.id })

    @Test fun `query filters non-matching and sorts by weight`() =
        assertEquals(listOf(101L, 100L), rankRecipients(all, "Kowal").map { it.id })
}
