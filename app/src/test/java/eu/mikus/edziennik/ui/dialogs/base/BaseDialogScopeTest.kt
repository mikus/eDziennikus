/*
 * Copyright (c) Mikolaj Olszewski 2026-11-12.
 */
package eu.mikus.edziennik.ui.dialogs.base

import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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
 * Pins that a dialog's coroutine scope ends with the dialog. Work a dialog started must not outlive
 * it: `EventManualDialog`'s date build resumed after CANCEL and showed an uncancelable "please wait"
 * dialog, which nothing could dismiss once the dialog had unregistered from EventBus.
 *
 * Every test dismisses through `dialog.dismiss()`, the route the buttons, back and the subclasses'
 * own code take, not through BaseDialog's protected `dismiss()` helper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BaseDialogScopeTest {

    private class Probe(activity: AppCompatActivity, onDismissListener: (String) -> Unit) :
        BaseDialog<Any>(activity, onDismissListener = onDismissListener) {
        override val TAG = "Probe"
        var dismissed = 0

        override fun getTitleRes(): Int? = null
        override fun getPositiveButtonText() = R.string.ok
        override suspend fun onShow() = Unit
        override fun onDismiss() {
            dismissed++
        }

        fun start(block: suspend () -> Unit) = launch { block() }
    }

    private var listenerCalls = 0

    @Test
    fun `work launched after the dialog is dismissed never runs`() {
        val (probe, dialog) = shown()
        dialog.dismiss()
        idleMainLooper()

        // An activity-scoped observer, for one, can call back into a dismissed dialog.
        var ran = false
        probe.start { ran = true }
        idleMainLooper()

        assertFalse("work launched after dismissal ran", ran)
    }

    @Test
    fun `work in flight when the dialog is dismissed does not resume`() {
        val (probe, dialog) = shown()
        val gate = CompletableDeferred<Unit>()
        var resumed = false
        probe.start {
            gate.await()
            resumed = true
        }
        idleMainLooper()

        dialog.dismiss()
        idleMainLooper()
        gate.complete(Unit)
        idleMainLooper()

        assertFalse("work in flight at dismissal resumed after it", resumed)
    }

    @Test
    fun `work launched while the dialog is showing runs`() {
        val (probe, _) = shown()

        var ran = false
        probe.start { ran = true }
        idleMainLooper()

        assertTrue("work launched while showing did not run", ran)
    }

    @Test
    fun `a button whose handler returns true still closes the dialog and runs the dismiss callbacks`() {
        val (probe, dialog) = shown()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMainLooper()

        assertFalse("the dialog is still showing", dialog.isShowing)
        assertEquals(1, probe.dismissed)
        assertEquals(1, listenerCalls)
    }

    private fun shown(): Pair<Probe, AlertDialog> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val probe = Probe(activity) { listenerCalls++ }
        probe.show()
        idleMainLooper()
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertTrue("the dialog never showed", dialog?.isShowing == true)
        return probe to dialog!!
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()
}
