/*
 * Copyright (c) Mikolaj Olszewski 2026-11-13.
 */
package eu.mikus.edziennik.ui.timetable

import android.app.Dialog
import android.content.DialogInterface
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.events.ApiTaskFinishedEvent
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.data.db.dao.TimetableDao
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.models.Week
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Pins that this dialog's work does not outlive it.
 *
 * Choosing a week whose lessons are not downloaded makes [GenerateBlockTimetableDialog] show an
 * uncancelable "please wait" dialog and enqueue a sync. That wait dialog's only dismissers were the
 * dialog's EventBus handlers, which its dismiss listener unregisters. So a generation that resumed
 * from its Room read after the user had dismissed the dialog left a wait dialog that nothing could
 * close (item 99). The class is not a `BaseDialog`, so the base-class cancel of item 93 does not
 * reach it. The fix stops a generation after the read when the dialog is gone, and has the dismiss
 * listener dismiss a wait dialog that is already up.
 *
 * Tests 6 and 7 need the read's resume to be queued on the main looper *before* the dismissal. The
 * real `App` boots here and its own background work can post to the main looper too, so they wait
 * for the read's `withContext` to complete rather than for any message, and they assert that the
 * generation had finished by the time the dismiss listener ran: a run in the wrong order fails
 * instead of passing without exercising the race.
 *
 * The read is held on a latch so a dismissal can land deterministically while it is in flight: the
 * timetable DAO is a mock whose `getBetweenDatesNow` blocks until the test releases it. `App.db` and
 * `App.profile` are `private set`, so they are swapped by reflection and restored afterwards.
 *
 * Test 4 is a characterisation of the success path, which dismisses its own dialog half way through
 * the launch. It is what turns red if the dialog's coroutine `Job` is cancelled on dismissal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GenerateBlockTimetableDialogTest {

    private lateinit var activity: AppCompatActivity
    private val dao = mockk<TimetableDao>()
    private val readEntered = CountDownLatch(1)
    private val readRelease = CountDownLatch(1)
    private var lessons: List<LessonFull> = emptyList()
    /** Whether the generation had finished when the dismiss listener ran; null until it runs. */
    private var launchDoneAtDismiss: Boolean? = null

    private val dbField = App::class.java.getDeclaredField("db").apply { isAccessible = true }
    private val profileField = App::class.java.getDeclaredField("profile").apply { isAccessible = true }
    private var savedDb: Any? = null
    private var savedProfile: Any? = null

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        savedDb = dbField.get(null)
        savedProfile = profileField.get(null)
        profileField.set(null, Profile(PROFILE_ID, PROFILE_ID, LoginType.DEMO, name = "Test"))
        every { dao.getBetweenDatesNow(any(), any()) } answers {
            readEntered.countDown()
            check(readRelease.await(5, SECONDS)) { "the read was never released" }
            lessons
        }
        dbField.set(null, mockk<AppDb>(relaxed = true) { every { timetableDao() } returns dao })
        idle()
        shadowOf(activity.application).clearStartedServices()
    }

    @After
    fun tearDown() {
        readRelease.countDown()
        ShadowDialog.getShownDialogs().forEach { it.dismiss() }
        idle()
        EventBus.getDefault().removeAllStickyEvents()
        dbField.set(null, savedDb)
        profileField.set(null, savedProfile)
    }

    @Test
    fun `dismissed during the read of an empty week, no wait dialog shows and no sync starts`() {
        val (dlg, main) = open()
        holdRead(main)

        main.dismiss()
        idle()
        readRelease.countDown()
        awaitDone(dlg)

        assertFalse("a wait dialog showed after the dialog was dismissed", everShown(R.string.please_wait))
        assertFalse("a sync started after the dialog was dismissed", syncStarted())
    }

    @Test
    fun `left open during the read of an empty week, the wait dialog shows and a sync starts`() {
        val (dlg, main) = open()
        holdRead(main)

        readRelease.countDown()
        awaitDone(dlg)

        assertTrue("an empty week no longer shows the wait dialog", showing(R.string.please_wait))
        assertTrue("an empty week no longer starts a sync", syncStarted())
    }

    @Test
    fun `a generation started after the dialog was dismissed shows no wait dialog`() {
        val (dlg, main) = open()
        readRelease.countDown()
        main.dismiss()
        idle()

        // An event racing the posted unregister, or the date picker, which outlives this dialog.
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)

        assertFalse("a generation started after dismissal showed a wait dialog", everShown(R.string.please_wait))
        assertFalse("a generation started after dismissal started a sync", syncStarted())
    }

    @Test
    fun `left open during the read of a week with lessons, the progress dialog closes and the share dialog shows`() {
        lessons = listOf(lesson())
        val (dlg, main) = open()
        holdRead(main)

        readRelease.countDown()
        awaitDone(dlg)

        assertFalse("the progress dialog is still showing", showing(R.string.timetable_generate_progress_title))
        assertTrue("the Share/Open dialog never showed", showing(R.string.timetable_generate_success_title))
    }

    @Test
    fun `dismissed during the read of a week with lessons, nothing is generated`() {
        lessons = listOf(lesson())
        val (dlg, main) = open()
        holdRead(main)

        main.dismiss()
        idle()
        readRelease.countDown()
        awaitDone(dlg)

        assertFalse("generation started after the dialog was dismissed", everShown(R.string.timetable_generate_progress_title))
        assertFalse("a success dialog showed after the dialog was dismissed", everShown(R.string.timetable_generate_success_title))
    }

    @Test
    fun `dismissed while the read's resume is already queued, no wait dialog shows and no sync starts`() {
        val (dlg, main) = open()
        holdRead(main)
        readRelease.countDown()
        awaitResumeQueued(dlg)

        // Back's route: dismiss() runs at once and posts the dismiss listener behind the queued resume.
        main.dismiss()
        awaitDone(dlg)

        assertEquals("the resume did not run before the dismiss listener", true, launchDoneAtDismiss)
        assertFalse("a wait dialog showed after the dialog was dismissed", everShown(R.string.please_wait))
        assertFalse("a sync started after the dialog was dismissed", syncStarted())
    }

    @Test
    fun `Cancel tapped while the read's resume is already queued leaves no wait dialog behind`() {
        val (dlg, main) = open()
        holdRead(main)
        readRelease.countDown()
        awaitResumeQueued(dlg)

        // AlertController posts the click, so the resume runs first and shows the wait dialog while
        // this dialog is still up; Cancel's dismiss() then unregisters the wait dialog's dismissers.
        main.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        awaitDone(dlg)
        idle()

        assertEquals("the resume did not run before the dismiss listener", true, launchDoneAtDismiss)
        assertTrue("the race did not run: no wait dialog showed before Cancel", everShown(R.string.please_wait))
        assertFalse("the dialog is still showing", main.isShowing)
        assertFalse("a wait dialog outlived the dialog", showing(R.string.please_wait))
    }

    private fun open(): Pair<GenerateBlockTimetableDialog, AlertDialog> {
        lateinit var dlg: GenerateBlockTimetableDialog
        dlg = GenerateBlockTimetableDialog(activity, onDismissListener = {
            launchDoneAtDismiss = dlg.coroutineContext[Job]!!.children.none()
        })
        idle()
        val main = ShadowDialog.getLatestDialog() as? AlertDialog
        assertTrue("the dialog never showed", main?.isShowing == true)
        return dlg to main!!
    }

    /** Taps Save (the current week is preselected) and returns once the read is blocked in the DAO. */
    private fun holdRead(main: AlertDialog) {
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue("the read never started", readEntered.await(5, SECONDS))
    }

    private fun awaitDone(dlg: GenerateBlockTimetableDialog) {
        val job = dlg.coroutineContext[Job]!!
        repeat(1000) {
            idle()
            if (job.children.none()) return
            Thread.sleep(5)
        }
        fail("the generation never finished")
    }

    /**
     * Returns once the read's `withContext` coroutine has completed, i.e. once its resume is posted to
     * the main looper, without running it. Not "the looper has a message": the real `App` posts too.
     */
    private fun awaitResumeQueued(dlg: GenerateBlockTimetableDialog) {
        val generation = dlg.coroutineContext[Job]!!.children.single()
        repeat(1000) {
            if (generation.children.none()) {
                Thread.sleep(50) // the resume is dispatched just after the child completes
                assertFalse("the read's resume was not queued", shadowOf(Looper.getMainLooper()).isIdle)
                return
            }
            Thread.sleep(5)
        }
        fail("the read never completed")
    }

    private fun titleOf(dialog: Dialog) =
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text?.toString()

    private fun everShown(titleRes: Int) =
        ShadowDialog.getShownDialogs().any { titleOf(it) == activity.getString(titleRes) }

    private fun showing(titleRes: Int) =
        ShadowDialog.getShownDialogs().any { it.isShowing && titleOf(it) == activity.getString(titleRes) }

    private fun syncStarted() = shadowOf(activity.application).nextStartedService != null

    private fun lesson() = LessonFull(PROFILE_ID, 1).apply {
        type = Lesson.TYPE_NORMAL
        date = Week.getWeekStart()
        startTime = Time(8, 0, 0)
        endTime = Time(8, 45, 0)
        subjectName = "Matematyka"
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val PROFILE_ID = 9299
    }
}
