/*
 * Copyright (c) Mikolaj Olszewski 2026-11-16.
 */
package eu.mikus.edziennik.ui.event

import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Pins which sync events end [EventManualDialog]'s "please wait" (item 109).
 *
 * Opening the dialog on a date with no lessons makes its `syncTimetable` show an uncancelable wait
 * and enqueue a TIMETABLE sync for the dialog's own profile. Only an error that can be that sync's
 * ends the wait: one stamped with the dialog's profile, or one with no profile, which is what the
 * real `IApiTask.enqueue` posts, inline, when the service start is refused. Another profile's error
 * and a `-1` error (SzkolnyTask, ErrorReportTask, a late error) leave it up. The queue draining
 * always ends it.
 *
 * The dialog is opened for [PROFILE_ID] while the app's current profile is [OTHER_ID], as it is when
 * an event of another profile is edited, so a comparison against `App.profileId` fails here.
 *
 * Everything runs through the real path: a profile row in the real Robolectric `App.db`, the
 * dialog's `ComposeView` composing in its window, `loadInitial` finding no lessons and calling
 * `syncTimetable`. The handlers are then called directly, as EventBus would on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventManualDialogSyncWaitTest {

    private lateinit var activity: AppCompatActivity

    private val profileField = App::class.java.getDeclaredField("profile").apply { isAccessible = true }
    private var savedProfile: Any? = null

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        savedProfile = profileField.get(null)
        profileField.set(null, Profile(OTHER_ID, OTHER_ID, LoginType.DEMO, name = "Current"))
        // The dialog's own profile exists in the database, with no lessons: loadInitial syncs.
        val profileDao = (activity.application as App).db.profileDao()
        // Same guard as ProfileSaveTest and LabProfilePersistTest: App.db is one instance for the
        // whole test JVM, so this both checks nothing leaked in and pins tearDown's own delete.
        assertNull("profile $PROFILE_ID left over from another test", profileDao.getByIdNow(PROFILE_ID))
        profileDao.add(Profile(PROFILE_ID, PROFILE_ID, LoginType.DEMO, name = "Dialog's"))
        Spy.refusal = null
    }

    @After
    fun tearDown() {
        ShadowDialog.getShownDialogs().forEach { it.dismiss() }
        idle()
        EventBus.getDefault().removeAllStickyEvents()
        profileField.set(null, savedProfile)
        // App.db is a process singleton shared by every Robolectric suite in this JVM, so the row
        // setUp added has to go: left behind, it is what App.onCreate's profileDao().firstId
        // fallback would load in a later suite that boots the real App.
        (activity.application as App).db.profileDao().remove(PROFILE_ID)
    }

    @Test
    fun `another profile's sync error while waiting leaves the wait up`() {
        val dlg = open()

        dlg.onApiTaskErrorEvent(error(OTHER_ID))
        idle()

        assertTrue("another profile's error ended the wait", waitShowing())
    }

    @Test
    fun `an error from no profile's task while waiting leaves the wait up`() {
        val dlg = open()

        // ApiService stamps -1 for SzkolnyTask, ErrorReportTask, or an error after the task was cleared.
        dlg.onApiTaskErrorEvent(error(-1))
        idle()

        assertTrue("a -1 error ended the wait", waitShowing())
    }

    @Test
    fun `this dialog's profile's sync error ends the wait`() {
        val dlg = open()

        dlg.onApiTaskErrorEvent(error(PROFILE_ID))
        idle()

        assertFalse("the wait outlived its own sync's error", waitShowing())
    }

    @Test
    fun `the wait's sync is enqueued for this dialog's profile, not the current one`() {
        open()

        // The other half of the filter. onApiTaskErrorEvent decides whose an error is by comparing
        // it against this dialog's profileId, so the sync it is waiting for has to be the one
        // enqueued for that same profileId. Enqueue it for App.profileId instead and every other
        // test in this class stays green, while the dialog's own error is read as foreign and the
        // uncancelable wait outlives it -- item 109 with the sign reversed. The task is read off
        // the bus because that is where IApiTask.enqueue posts it, as the block dialog's test does.
        val task = EventBus.getDefault().getStickyEvent(EdziennikTask::class.java)
        assertNotNull("no sync task was enqueued", task)
        assertEquals(
            "the sync was not enqueued for the profile the dialog filters errors against",
            PROFILE_ID,
            task!!.profileId,
        )
    }

    @Test
    fun `a refused sync start ends the wait it began`() {
        // The real IApiTask.enqueue catches the refusal and posts an error with no profile, inline.
        activity = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()
        shadowOf(activity.application).clearStartedServices()
        val dialog = EventManualDialog(activity, PROFILE_ID)
        EventBus.getDefault().register(Spy)
        try {
            dialog.show()
            awaitWaitShownOnce()
        } finally {
            EventBus.getDefault().unregister(Spy)
        }

        assertNull("the refusal posted a stamped error", Spy.refusal!!.error.profileId)
        assertFalse("the wait outlived the refused start", waitShowing())
        assertNull("a service started", shadowOf(activity.application).nextStartedService)
    }

    @Test
    fun `the queue draining ends the wait`() {
        val dlg = open()

        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        idle()

        assertFalse("the wait outlived the queue", waitShowing())
    }

    /** Refuses a foreground-service start, as Android 12+ does from a background process. */
    class RefusingActivity : AppCompatActivity() {
        override fun startForegroundService(service: Intent?): ComponentName? =
            throw IllegalStateException("refused")
    }

    /** Records the refusal's error, so the test sees what the real enqueue posted. */
    object Spy {
        var refusal: ApiTaskErrorEvent? = null

        @org.greenrobot.eventbus.Subscribe
        fun onError(event: ApiTaskErrorEvent) {
            refusal = event
        }
    }

    /** Opens the dialog for [PROFILE_ID] and returns once its wait is up. */
    private fun open(): EventManualDialog {
        val dialog = EventManualDialog(activity, PROFILE_ID)
        dialog.show()
        repeat(500) {
            idle()
            if (waitShowing()) return dialog
            Thread.sleep(10)
        }
        fail("the wait never showed")
        throw AssertionError()
    }

    /** For the refusal: the wait shows and is dismissed inside one turn, so wait for it to have shown. */
    private fun awaitWaitShownOnce() {
        repeat(500) {
            idle()
            if (Spy.refusal != null) {
                assertTrue("the wait never showed", everShown())
                return
            }
            Thread.sleep(10)
        }
        fail("the refused start never reported an error")
    }

    private fun titleOf(dialog: Dialog) =
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text?.toString()

    private fun isWait(dialog: Dialog) = titleOf(dialog) == activity.getString(R.string.please_wait)

    private fun everShown() = ShadowDialog.getShownDialogs().any(::isWait)

    private fun waitShowing() = ShadowDialog.getShownDialogs().any { it.isShowing && isWait(it) }

    private fun error(profileId: Int?) =
        ApiTaskErrorEvent(ApiError("test", 1).apply { this.profileId = profileId })

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val PROFILE_ID = 9311
        const val OTHER_ID = 9312
    }
}
