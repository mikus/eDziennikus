/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile

/**
 * Pure selection filter for the Sync step: the selected profiles + the login stores those
 * profiles belong to. Mirrors LoginSyncFragment.onViewCreated's filter exactly.
 */
object LoginSyncSelection {
    data class Selection(val profiles: List<Profile>, val loginStores: List<LoginStore>)

    fun selectedForSync(
        items: List<LoginViewModel.LoginSummaryItem>,
        stores: List<LoginStore>,
    ): Selection {
        val profiles = items.filter { it.isSelected }.map { it.profile }
        val loginStores = stores.filter { store -> profiles.any { it.loginStoreId == store.id } }
        return Selection(profiles, loginStores)
    }
}
