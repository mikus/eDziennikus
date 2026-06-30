/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HomeCardOrderTest {

    private fun cards(vararg ids: Int) = ids.map { HomeCardModel(profileId = 1, cardId = it) }
    private fun ids(list: List<HomeCardModel>) = list.map { it.cardId }

    @Test
    fun `swap reorders two entries by id`() {
        val out = HomeCardOrder.swap(cards(1, 2, 3, 4), fromId = 2, toId = 4)
        assertEquals(listOf(1, 4, 3, 2), ids(out))
    }

    @Test
    fun `swap is a no-op when either id is missing`() {
        val input = cards(1, 2, 3)
        assertEquals(listOf(1, 2, 3), ids(HomeCardOrder.swap(input, fromId = 2, toId = 99)))
    }

    @Test
    fun `swap refuses pinned ids`() {
        val input = cards(1, 2, 101)
        assertEquals(listOf(1, 2, 101), ids(HomeCardOrder.swap(input, fromId = 2, toId = 101)))
    }

    @Test
    fun `remove drops the entry`() {
        assertEquals(listOf(1, 3), ids(HomeCardOrder.remove(cards(1, 2, 3), cardId = 2)))
    }

    @Test
    fun `remove refuses pinned ids and missing ids`() {
        assertEquals(listOf(1, 101), ids(HomeCardOrder.remove(cards(1, 101), cardId = 101)))
        assertEquals(listOf(1, 101), ids(HomeCardOrder.remove(cards(1, 101), cardId = 50)))
    }

    @Test
    fun `mergeForProfile replaces this profile's entries and preserves others`() {
        val all = listOf(
            HomeCardModel(profileId = 2, cardId = 1),
            HomeCardModel(profileId = 1, cardId = 5),
            HomeCardModel(profileId = 2, cardId = 3),
        )
        val out = HomeCardOrder.mergeForProfile(all, profileId = 1, profileCards = cards(9, 8))
        assertEquals(
            listOf(2 to 1, 2 to 3, 1 to 9, 1 to 8),
            out.map { it.profileId to it.cardId },
        )
    }
}
