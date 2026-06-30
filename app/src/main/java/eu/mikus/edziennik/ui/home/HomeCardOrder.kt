/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

/**
 * Pure read-modify-write transforms over the FULL per-profile card list, mirroring the legacy
 * HomeFragment.swapCards / removeCard. Pinned cards (cardId >= 100: Archive 101 / Availability 102)
 * are never moved or removed. Operating on the full list (not the gated display list) means a card
 * that is merely feature-gated-off right now is never dropped from persistence.
 */
object HomeCardOrder {

    private fun isPinned(cardId: Int) = cardId >= HomeCardUi.PINNED_ID_FLOOR

    fun swap(cards: List<HomeCardModel>, fromId: Int, toId: Int): List<HomeCardModel> {
        if (isPinned(fromId) || isPinned(toId)) return cards
        val from = cards.indexOfFirst { it.cardId == fromId }
        val to = cards.indexOfFirst { it.cardId == toId }
        if (from == -1 || to == -1 || from == to) return cards
        return cards.toMutableList().also {
            val tmp = it[from]; it[from] = it[to]; it[to] = tmp
        }
    }

    fun remove(cards: List<HomeCardModel>, cardId: Int): List<HomeCardModel> {
        if (isPinned(cardId)) return cards
        return cards.filterNot { it.cardId == cardId }
    }

    /**
     * Persistence merge: replace [profileId]'s entries in the full multi-profile [all] list with
     * [profileCards], keeping every other profile's entries untouched. Used by the Factory's
     * saveCards seam so a profile's write never clobbers another's.
     */
    fun mergeForProfile(all: List<HomeCardModel>, profileId: Int, profileCards: List<HomeCardModel>): List<HomeCardModel> =
        all.filter { it.profileId != profileId } + profileCards
}
