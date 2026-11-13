/*
 * Copyright (c) Mikolaj Olszewski 2026-11-13.
 */
package eu.mikus.edziennik.ui.dialogs

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins that neither button dismisses this dialog once Remove has been tapped.
 *
 * The removal runs in a `withContext(Dispatchers.Default)` block and only afterwards, back on the
 * main thread, reloads the screen, shows the "profile removed" toast and calls
 * [ProfileRemoveDialog.onRemove]. Dismissing cancels the dialog's coroutine scope
 * ([eu.mikus.edziennik.ui.dialogs.base.BaseDialog.show]) and that block has no suspension point of
 * its own, so a dismissal while it runs deletes the profile and all of its rows and then skips every
 * one of those three steps: the screen keeps rendering the deleted profile, nothing is said, and the
 * caller is never told it happened. The wipe is 24 table clears with no wrapping transaction, so on
 * a real diary the dialog sits there long enough to invite exactly that second tap.
 *
 * Back press and outside taps are already refused (`isCancelable() = false`); the two buttons were
 * the routes left.
 *
 * The fixture profile does not exist, and `removeProfileData` returns before it touches a table for
 * an unknown id, so nothing here writes to the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileRemoveDialogCancelTest {

    @Test
    fun `Cancel dismisses while Remove has not been tapped`() = runBlocking {
        val dialog = shown()

        assertTrue("Cancel no longer closes a dialog that has not started removing", dialog.onNeutralClick())
    }

    @Test
    fun `Cancel does not dismiss once Remove has started`() = runBlocking {
        val dialog = shown()
        dialog.onPositiveClick()

        assertFalse("Cancel dismissed a dialog whose removal had started", dialog.onNeutralClick())
    }

    @Test
    fun `a second Remove does not dismiss while the first is running`() = runBlocking {
        val dialog = shown()
        dialog.onPositiveClick()

        assertFalse("a second Remove dismissed the dialog", dialog.onPositiveClick())
    }

    private fun shown(): ProfileRemoveDialog {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val dialog = ProfileRemoveDialog(activity, UNKNOWN_PROFILE_ID, "Stored", onRemove = {})
        dialog.show()
        shadowOf(Looper.getMainLooper()).idle()
        return dialog
    }

    private companion object {
        /** No such profile: `removeProfileData` returns before it touches a table. */
        const val UNKNOWN_PROFILE_ID = 9242
    }
}
