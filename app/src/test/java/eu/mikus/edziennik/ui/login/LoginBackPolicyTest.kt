/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LoginBackPolicyTest {

    @Test fun `progress sync syncError finish consume back regardless of stores`() {
        for (r in listOf(LoginRoute.PROGRESS, LoginRoute.SYNC, LoginRoute.SYNC_ERROR, LoginRoute.FINISH)) {
            assertEquals(LoginBackAction.Consume, loginBackPolicy(r, hasLoginStores = false))
            assertEquals(LoginBackAction.Consume, loginBackPolicy(r, hasLoginStores = true))
        }
    }

    @Test fun `summary confirms cancel regardless of stores`() {
        assertEquals(LoginBackAction.ConfirmCancel, loginBackPolicy(LoginRoute.SUMMARY, false))
        assertEquals(LoginBackAction.ConfirmCancel, loginBackPolicy(LoginRoute.SUMMARY, true))
    }

    @Test fun `chooser first entry cancels to host`() {
        assertEquals(LoginBackAction.CancelToHost, loginBackPolicy(LoginRoute.CHOOSER, hasLoginStores = false))
    }

    @Test fun `chooser during add-student goes up to summary`() {
        assertEquals(LoginBackAction.Up, loginBackPolicy(LoginRoute.CHOOSER, hasLoginStores = true))
    }

    @Test fun `form goes up`() {
        assertEquals(LoginBackAction.Up, loginBackPolicy(LoginRoute.FORM, false))
    }

    @Test fun `unknown or null route goes up`() {
        assertEquals(LoginBackAction.Up, loginBackPolicy(null, false))
        assertEquals(LoginBackAction.Up, loginBackPolicy("nonsense", true))
    }
}
