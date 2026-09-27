/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeCardOrderTest {

    private fun cards(vararg ids: Int) = ids.map { HomeCardModel(profileId = 1, cardId = it) }
    private fun ids(list: List<HomeCardModel>?) = list?.map { it.cardId }

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

    private val offered = listOf(1, 2, 3, 4, 5)

    @Test
    fun `applySelection returns null when the selection matches what is stored`() {
        assertNull(
            HomeCardOrder.applySelection(
                all = cards(1, 2, 4, 3, 5), profileId = 1,
                selected = setOf(1, 2, 3, 4, 5), offered = offered,
            )
        )
    }

    @Test
    fun `applySelection keeps the stored order and appends newly checked cards`() {
        // setOf(1, 2, 5, 3) iterates 5 before 3 on purpose: the expectation below holds only if the
        // additions are ordered by `offered`, not by the selection. Do not "tidy" it to 1, 2, 3, 5.
        val out = HomeCardOrder.applySelection(
            all = cards(2, 1), profileId = 1,
            selected = setOf(1, 2, 5, 3), offered = offered,
        )
        assertEquals(listOf(2, 1, 3, 5), ids(out))
    }

    @Test
    fun `applySelection drops unchecked cards without reordering the survivors`() {
        val out = HomeCardOrder.applySelection(
            all = cards(1, 2, 4, 3, 5), profileId = 1,
            selected = setOf(1, 2, 3, 4), offered = offered,
        )
        assertEquals(listOf(1, 2, 4, 3), ids(out))
    }

    @Test
    fun `applySelection preserves a stored card the dialog does not offer`() {
        val out = HomeCardOrder.applySelection(
            all = cards(1, 2, 99), profileId = 1,
            selected = setOf(1), offered = offered,
        )
        assertEquals(listOf(1, 99), ids(out))
    }

    @Test
    fun `applySelection never removes or mints a pinned id even when it is offered`() {
        val offeredWithPinned = listOf(1, 2, 101)
        val kept = HomeCardOrder.applySelection(
            all = cards(1, 2, 101), profileId = 1,
            selected = setOf(1), offered = offeredWithPinned,
        )
        assertEquals(listOf(1, 101), ids(kept))
        assertNull(
            HomeCardOrder.applySelection(
                all = cards(1, 2), profileId = 1,
                selected = setOf(1, 2, 101), offered = offeredWithPinned,
            )
        )
    }

    @Test
    fun `applySelection leaves other profiles' entries alone`() {
        val all = listOf(
            HomeCardModel(profileId = 2, cardId = 1),
            HomeCardModel(profileId = 1, cardId = 5),
            HomeCardModel(profileId = 2, cardId = 3),
        )
        val out = HomeCardOrder.applySelection(
            all = all, profileId = 1, selected = setOf(1, 5), offered = offered,
        )
        assertEquals(
            listOf(2 to 1, 2 to 3, 1 to 5, 1 to 1),
            out?.map { it.profileId to it.cardId },
        )
    }

    @Test
    fun `applySelection writes nothing when every card would be unchecked`() {
        assertNull(
            HomeCardOrder.applySelection(
                all = cards(2, 1), profileId = 1, selected = emptySet(), offered = offered,
            )
        )
    }
}
