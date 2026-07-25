/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LoginSyncSelectionTest {

    private fun item(id: Int, storeId: Int, selected: Boolean): LoginViewModel.LoginSummaryItem {
        val p = mockk<Profile>()
        every { p.id } returns id
        every { p.loginStoreId } returns storeId
        return LoginViewModel.LoginSummaryItem(profile = p, modeIcon = 0, isSelected = selected)
    }

    private fun store(id: Int): LoginStore = mockk<LoginStore>().also { every { it.id } returns id }

    @Test fun `none selected yields empty`() {
        val r = LoginSyncSelection.selectedForSync(
            listOf(item(1, 10, false), item(2, 10, false)), listOf(store(10)))
        assertEquals(0, r.profiles.size)
        assertEquals(0, r.loginStores.size)
    }

    @Test fun `subset selected keeps only those profiles and their stores`() {
        val r = LoginSyncSelection.selectedForSync(
            listOf(item(1, 10, true), item(2, 20, false)), listOf(store(10), store(20)))
        assertEquals(listOf(1), r.profiles.map { it.id })
        assertEquals(listOf(10), r.loginStores.map { it.id })
    }

    @Test fun `store with no selected profile is dropped`() {
        val r = LoginSyncSelection.selectedForSync(
            listOf(item(1, 10, true)), listOf(store(10), store(99)))
        assertEquals(listOf(10), r.loginStores.map { it.id })
    }

    @Test fun `multiple profiles sharing a store keep it once`() {
        val r = LoginSyncSelection.selectedForSync(
            listOf(item(1, 10, true), item(2, 10, true)), listOf(store(10)))
        assertEquals(listOf(1, 2), r.profiles.map { it.id })
        assertEquals(listOf(10), r.loginStores.map { it.id })
    }
}
