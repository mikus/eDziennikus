/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.home

/**
 * Pure transforms over the persisted home-card list, mirroring the pre-Compose
 * HomeFragment.swapCards / removeCard (both removed in 52fd2b4d). Pinned cards
 * (cardId >= 100: Archive 101 / Availability 102) are never moved, removed
 * or minted. Operating on the persisted list (not the gated display list) means a card that is
 * merely feature-gated-off right now is never dropped from persistence.
 *
 * Two contracts, distinguished by the first parameter's name:
 *  - [swap] and [remove] take ONE profile's list (`cards`); [remove] returns null for "nothing to
 *    write", as [applySelection] does.
 *  - [mergeForProfile] and [applySelection] take and return the FULL list (`all`), so a write for
 *    one profile never clobbers another's.
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

    /**
     * Drop [cardId] from one profile's [cards].
     *
     * Returns null when there is nothing to write, for three separate reasons:
     *  - [cardId] is pinned. Pinned cards are never dropped from persistence, so the request is
     *    refused outright. This guard is NOT subsumed by the trailing `takeIf`: for a pinned id that
     *    is present, `filterNot` would happily drop it and the result would differ from [cards].
     *  - [cardId] is absent, so the result would equal [cards].
     *  - dropping it would leave the profile with no cards, which HomeViewModel.seedIfEmpty would
     *    immediately revert to the defaults — undoing every removal the user had made. The same
     *    refusal as [applySelection], for the same reason: swiping cards away one at a time is the
     *    other way to empty a profile.
     *
     * The caller must act on the refusal rather than swallow it. HomeScreen resets its
     * SwipeToDismissBox when HomeViewModel.removeCard returns false; without that the card stays
     * dismissed off a list that still holds it, and the box disables its own gestures while settled
     * away from Settled, so it could not even be swiped again.
     */
    fun remove(cards: List<HomeCardModel>, cardId: Int): List<HomeCardModel>? {
        if (isPinned(cardId)) return null
        val kept = cards.filterNot { it.cardId == cardId }
        if (kept.isEmpty())
            return null
        return kept.takeIf { it != cards }
    }

    /**
     * Persistence merge: replace [profileId]'s entries in the full multi-profile [all] list with
     * [profileCards], keeping every other profile's entries untouched. Used by the Factory's
     * saveCards seam so a profile's write never clobbers another's.
     */
    fun mergeForProfile(all: List<HomeCardModel>, profileId: Int, profileCards: List<HomeCardModel>): List<HomeCardModel> =
        all.filter { it.profileId != profileId } + profileCards

    /**
     * Reconcile [profileId]'s cards against a checkbox [selected] set WITHOUT reordering them.
     *
     * Surviving cards keep their stored order; newly-checked cards are appended in [offered] order.
     * A stored card outside [offered] is preserved — the dialog has no opinion about a type it does
     * not show — and a pinned id is never removed or minted even when [offered] lists it. [offered]
     * must be duplicate-free: a repeated id would mint a duplicate card, and HomeScreen keys its
     * LazyColumn by cardId, so a duplicate id crashes the screen.
     *
     * Returns null when there is nothing to write: the result equals [all], or it would leave
     * [profileId] with no cards, which HomeViewModel.seedIfEmpty would immediately revert to the
     * defaults, overwriting the stored order.
     *
     * The caller must also make sure the Home ViewModel is rebuilt after a write —
     * `HomeViewModel._cards` is a construction-time snapshot that never observes config, so a
     * ViewModel that survives the write would saveCards() its stale copy on the next reorder and
     * discard this one.
     */
    fun applySelection(
        all: List<HomeCardModel>,
        profileId: Int,
        selected: Set<Int>,
        offered: List<Int>,
    ): List<HomeCardModel>? {
        val kept = all.filter {
            it.profileId == profileId &&
                (it.cardId in selected || isPinned(it.cardId) || it.cardId !in offered)
        }
        val keptIds = kept.mapTo(mutableSetOf()) { it.cardId }
        val added = offered
            .filter { it in selected && it !in keptIds && !isPinned(it) }
            .map { HomeCardModel(profileId, it) }
        val profileCards = kept + added
        if (profileCards.isEmpty())
            return null
        return mergeForProfile(all, profileId, profileCards).takeIf { it != all }
    }
}
