/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.data.api.models

import com.google.gson.Gson
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class PendingUserActionTest {

    private val parked = PendingUserAction(
        profileId = 3,
        type = UserActionRequiredEvent.Type.RECAPTCHA,
        siteKey = "6Ld_key",
        referer = "https://portal.librus.pl/rodzina/login",
        userAgent = "Mozilla/5.0",
    )

    @Test
    fun `it round-trips through the config store's serialiser`() {
        // ConfigDelegate sends a top-level data class through gson.toJson/fromJson, so this is the
        // property the store actually needs: the notification outlives the process, and a cold start
        // that cannot read the parked action turns the tap into a silent no-op.
        val gson = Gson()
        val restored = gson.fromJson(gson.toJson(parked), PendingUserAction::class.java)
        assertEquals(parked, restored)
        assertEquals(UserActionRequiredEvent.Type.RECAPTCHA, restored.type)
    }

    @Test
    fun `a tap for the parked profile gets the action`() {
        assertEquals(parked, pendingFor(parked, profileId = 3))
    }

    @Test
    fun `a tap for another profile gets nothing rather than the wrong action`() {
        // One slot, so a second profile's park displaces the first. Running the wrong profile's
        // captcha would be worse than dropping it.
        assertNull(pendingFor(parked, profileId = 7))
        assertNull(pendingFor(parked, profileId = null))
        assertNull(pendingFor(null, profileId = 3))
    }
}
