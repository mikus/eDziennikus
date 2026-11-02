/*
 * Copyright (c) Mikolaj Olszewski 2026-11-2.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Pins what the profile settings dialog's Remove button opens, through [profileRemoveConfirmation],
 * which that button calls. The caller's callback must be the confirmation's Remove action, and Cancel
 * must close the confirmation without running it: the profile settings dialog skips its save when
 * that callback has run, so a Cancel that ran it threw the user's unsaved edits away.
 *
 * Nothing here touches the database: the confirmation is only built, or shown and cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileRemoveConfirmationTest {

    @Test
    fun `the caller's callback is the confirmation's Remove action`() {
        var removed = 0
        val confirmation = profileRemoveConfirmation(activity(), profile()) { removed++ }

        confirmation.onRemove()

        assertEquals(1, removed)
    }

    @Test
    fun `Cancel closes the confirmation without running the callback`() {
        var removed = 0
        profileRemoveConfirmation(activity(), profile()) { removed++ }.show()
        idleMainLooper()
        val shown = ShadowDialog.getLatestDialog() as? AlertDialog
        assertTrue("the confirmation never showed", shown?.isShowing == true)

        shown!!.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        idleMainLooper()

        assertFalse("Cancel did not close the confirmation", shown.isShowing)
        assertEquals(0, removed)
    }

    private fun activity(): AppCompatActivity =
        Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    private fun profile() = Profile(
        id = PROFILE_ID,
        loginStoreId = LOGIN_STORE_ID,
        loginStoreType = LoginType.LIBRUS,
        name = "Stored",
    )

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val PROFILE_ID = 9041
        const val LOGIN_STORE_ID = 9141
    }
}
