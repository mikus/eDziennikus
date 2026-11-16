/*
 * Copyright (c) Mikolaj Olszewski 2026-11-13.
 */
package eu.mikus.edziennik.ui.timetable

import android.app.Dialog
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskFinishedEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.data.db.dao.TimetableDao
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.models.Week
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.IOException
import java.io.OutputStream
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
 * A read can be held on a gate so a dismissal can land deterministically while it is in flight: the
 * timetable DAO is a mock whose `getBetweenDatesNow` returns at once, unless the test has armed the
 * gate with [armRead], in which case it blocks until the test releases it. The gate can be re-armed
 * for a later read. `App.db` and `App.profile` are `private set`, so they are swapped by reflection
 * and restored afterwards.
 *
 * Test 4 is a characterisation of the success path, which dismisses its own dialog half way through
 * the launch. It is what turns red if the dialog's coroutine `Job` is cancelled on dismissal.
 *
 * The dialog waits for its own sync in one state, set when it starts a wait and cleared when the wait
 * ends (item 101). Once a wait has ended, a later empty week must start a new one; a rebuild after a
 * sync must never start one, even when its read outlives the wait it was rebuilding for.
 *
 * A sync error closes the dialog only while it is waiting for its own sync, and only when the error
 * is this profile's or has no profile, as a refused service start has (item 103).
 *
 * A save reports success only when the image was written; any failure after the MediaStore row was
 * inserted deletes that row (item 104). With no provider registered, `ShadowContentResolver.insert`
 * returns `<uri>/<++counter>`, so [insertedUri] sets the counter one below the id it expects, and the
 * output-stream supplier is keyed on that exact uri.
 *
 * A failure while drawing the image, an `OutOfMemoryError` included, is reported like a failed save
 * rather than crashing (item 111); the image's own `Bitmap.createBitmap` is stubbed to throw.
 *
 * A week that is still empty after the dialog's own sync is reported as having no lessons, but only
 * once that sync is provably over (item 107): its task was started by the service (a `taskId` other
 * than 0, set here by hand, as no service runs), a Finished for the profile came after that, and
 * then the queue drained. Nothing else may say it: a Finished can be another task's, and a drain can
 * follow a task that never ran or was cut off. Tests that never start the task therefore see the
 * old, silent drain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GenerateBlockTimetableDialogTest {

    private lateinit var activity: AppCompatActivity
    private val dao = mockk<TimetableDao>()
    /** Counted down when a read enters the DAO while [gate] is armed; replaced on each [armRead]. */
    @Volatile private var readEntered = CountDownLatch(1)
    /** While non-null and not yet released, a read blocks on it. */
    @Volatile private var gate: CountDownLatch? = null
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
            gate?.let { armed ->
                readEntered.countDown()
                check(armed.await(5, SECONDS)) { "the read was never released" }
            }
            lessons
        }
        dbField.set(null, mockk<AppDb>(relaxed = true) { every { timetableDao() } returns dao })
        idle()
        shadowOf(activity.application).clearStartedServices()
    }

    @After
    fun tearDown() {
        release()
        ShadowDialog.getShownDialogs().forEach { it.dismiss() }
        idle()
        EventBus.getDefault().removeAllStickyEvents()
        unmockkStatic(Bitmap::class)
        dbField.set(null, savedDb)
        profileField.set(null, savedProfile)
    }

    @Test
    fun `dismissed during the read of an empty week, no wait dialog shows and no sync starts`() {
        val (dlg, main) = open()
        holdRead(main)

        main.dismiss()
        idle()
        release()
        awaitDone(dlg)

        assertFalse("a wait dialog showed after the dialog was dismissed", everShown(R.string.please_wait))
        assertFalse("a sync started after the dialog was dismissed", syncStarted())
    }

    @Test
    fun `left open during the read of an empty week, the wait dialog shows and a sync starts`() {
        val (dlg, main) = open()
        holdRead(main)

        release()
        awaitDone(dlg)

        assertTrue("an empty week no longer shows the wait dialog", showing(R.string.please_wait))
        assertTrue("an empty week no longer starts a sync", syncStarted())
    }

    @Test
    fun `a sync finishing after the dialog was dismissed starts nothing`() {
        val (dlg, main) = open()
        main.dismiss()
        idle()

        // Pins only that a Finished event with no wait set starts no read, wait or sync. The late
        // date-picker route goes through the post-read `!dialog.isShowing` guard, which `dismissed
        // during the read of an empty week, no wait dialog shows and no sync starts` pins.
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

        release()
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
        release()
        awaitDone(dlg)

        assertFalse("generation started after the dialog was dismissed", everShown(R.string.timetable_generate_progress_title))
        assertFalse("a success dialog showed after the dialog was dismissed", everShown(R.string.timetable_generate_success_title))
    }

    @Test
    fun `dismissed while the read's resume is already queued, no wait dialog shows and no sync starts`() {
        val (dlg, main) = open()
        holdRead(main)
        release()
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
        release()
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

    @Test
    fun `a sync this dialog did not start does not start a generation`() {
        val (dlg, main) = open()

        // The periodic sync, the launch sync or the Timetable screen's own refresh finishing.
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)

        verify(exactly = 0) { dao.getBetweenDatesNow(any(), any()) }
        assertFalse("an unrelated sync showed a wait dialog", everShown(R.string.please_wait))
        assertFalse("an unrelated sync started another sync", syncStarted())
        assertTrue("an unrelated sync closed the dialog", main.isShowing)
    }

    @Test
    fun `a sync this dialog did not start, on a day with a lesson, neither crashes nor generates`() {
        lessons = listOf(lesson(on = Date.getToday()))
        val (dlg, main) = open()
        val uncaught = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        thread.setUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
            awaitDone(dlg)
        } finally {
            thread.uncaughtExceptionHandler = previous
        }

        assertEquals("an unrelated sync threw", emptyList<Throwable>(), uncaught)
        verify(exactly = 0) { dao.getBetweenDatesNow(any(), any()) }
        assertFalse("an unrelated sync generated an image", everShown(R.string.timetable_generate_progress_title))
        assertTrue("an unrelated sync closed the dialog", main.isShowing)
    }

    @Test
    fun `the dialog's own sync finishing regenerates the week it was waiting for`() {
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        lessons = listOf(lesson())
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)

        assertFalse("the wait dialog is still showing", showing(R.string.please_wait))
        assertTrue("the synced week was not generated", showing(R.string.timetable_generate_success_title))
    }

    @Test
    fun `a sync that does not fill the week keeps the dialog waiting for one that does`() {
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        // Another task for this profile finishes first. The dialog's own sync is queued behind
        // whatever was already running, and ApiTaskFinishedEvent carries no task identity, so this
        // cannot be told apart from the dialog's own. The week is still empty.
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)

        assertTrue("an unrelated sync ended the wait", showing(R.string.please_wait))

        // Now the week the user asked for arrives.
        lessons = listOf(lesson())
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)

        assertFalse("the wait dialog outlived the week arriving", showing(R.string.please_wait))
        assertTrue(
            "the week the user asked for was never generated",
            showing(R.string.timetable_generate_success_title),
        )
    }

    @Test
    fun `when the queue drains with the week still empty, the wait ends and later syncs do not generate`() {
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        // Nothing is left to wait for: the week simply has no lessons to download.
        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        idle()
        assertFalse("the wait dialog outlived the queue", showing(R.string.please_wait))

        // The wait is over, so a later sync must not start generating on its own.
        lessons = listOf(lesson())
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)

        assertFalse("a later sync generated an image", everShown(R.string.timetable_generate_progress_title))
        assertTrue("a later sync closed the dialog", main.isShowing)
    }

    @Test
    fun `the regeneration reads the week that was waited for, not the current one`() {
        val (dlg, main) = open()
        // Next week, so "the week waited for" and "the current week" are different reads.
        main.findViewById<RadioGroup>(R.id.weekSelectionRadioGroup)!!.check(R.id.withChangesNextWeekRadio)
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        val nextStart = Week.getWeekEnd().clone().stepForward(0, 0, 1)
        val nextEnd = nextStart.clone().stepForward(0, 0, 6)
        lessons = listOf(lesson(on = nextStart))
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)

        // Both ends of both reads: the Save and the rebuild each span the whole week waited for.
        // `any()` on the end date would let a narrowed range through, and the DAO is a mock that
        // answers the same rows whatever dates it is given, so nothing else here would notice.
        verify(exactly = 2) {
            dao.getBetweenDatesNow(
                match { it.stringY_m_d == nextStart.stringY_m_d },
                match { it.stringY_m_d == nextEnd.stringY_m_d },
            )
        }
        verify(exactly = 0) {
            dao.getBetweenDatesNow(match { it.stringY_m_d == Week.getWeekStart().stringY_m_d }, any())
        }
    }

    @Test
    fun `the next-week radio shows next week's start and end dates`() {
        val (_, main) = open()
        val nextStart = Week.getWeekEnd().clone().stepForward(0, 0, 1)
        val nextEnd = nextStart.clone().stepForward(0, 0, 6)

        assertEquals(
            activity.getString(R.string.timetable_generate_next_week_format, nextStart.formattedStringShort, nextEnd.formattedStringShort),
            main.findViewById<TextView>(R.id.withChangesNextWeekRadio)!!.text.toString(),
        )
    }

    @Test
    fun `after the queue drains, a later empty week waits and syncs that week again`() {
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        idle()
        assertFalse("the wait dialog outlived the queue", showing(R.string.please_wait))
        assertTrue("the queue draining closed the dialog", main.isShowing)
        shadowOf(activity.application).clearStartedServices()
        EventBus.getDefault().removeStickyEvent(EdziennikTask::class.java)

        // A second empty week, after the first wait is over.
        main.findViewById<RadioGroup>(R.id.weekSelectionRadioGroup)!!.check(R.id.withChangesNextWeekRadio)
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)

        assertTrue("a later empty week did not show the wait dialog", showing(R.string.please_wait))
        assertTrue("a later empty week did not start a sync", syncStarted())
        val task = EventBus.getDefault().getStickyEvent(EdziennikTask::class.java)
        assertTrue("no sync task was enqueued", task != null)
        val nextStart = Week.getWeekEnd().clone().stepForward(0, 0, 1)
        assertEquals(
            "the sync was not for the week now waited for",
            nextStart.stringY_m_d,
            (task!!.request as EdziennikTask.SyncProfileRequest).arguments!!.get("weekStart").asString,
        )

        // Save again while that wait is on: keep waiting, enqueue nothing.
        shadowOf(activity.application).clearStartedServices()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)

        assertFalse("a second Save during the wait started another sync", syncStarted())
        assertEquals(
            "a second Save during the wait did not leave exactly one wait dialog",
            1,
            ShadowDialog.getShownDialogs().count { it.isShowing && titleOf(it) == activity.getString(R.string.please_wait) },
        )
    }

    @Test
    fun `a regeneration whose read outlives the wait never starts another sync`() {
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        armRead()
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        idle()
        assertTrue("the regeneration's read never started", readEntered.await(5, SECONDS))

        // The queue drains while the regeneration is still reading the (still empty) week.
        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        idle()
        assertFalse("the wait dialog outlived the queue", showing(R.string.please_wait))
        shadowOf(activity.application).clearStartedServices()

        release()
        awaitDone(dlg)

        assertFalse("the regeneration showed another wait dialog", showing(R.string.please_wait))
        assertFalse("the regeneration started another sync", syncStarted())
        assertTrue("the regeneration closed the dialog", main.isShowing)
    }

    @Test
    fun `another profile's sync error while waiting leaves both dialogs up`() {
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        dlg.onApiTaskErrorEvent(error(PROFILE_ID + 1))
        idle()

        assertTrue("another profile's error ended the wait", showing(R.string.please_wait))
        assertTrue("another profile's error closed the dialog", main.isShowing)
    }

    @Test
    fun `a sync error while the dialog is not waiting leaves it up`() {
        val (dlg, main) = open()

        // A background sync of this profile failing while the user is still choosing the options.
        dlg.onApiTaskErrorEvent(error(PROFILE_ID))
        idle()

        assertTrue("an error the dialog was not waiting for closed it", main.isShowing)
    }

    @Test
    fun `this profile's sync error while waiting closes both dialogs`() {
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))

        dlg.onApiTaskErrorEvent(error(PROFILE_ID))
        idle()

        assertFalse("the wait dialog outlived the error", showing(R.string.please_wait))
        assertFalse("the dialog outlived the error", main.isShowing)
    }

    @Test
    fun `a refused sync start ends the wait it began`() {
        // The real IApiTask.enqueue catches the refusal and posts an error with no profile, inline.
        activity = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        idle()

        assertTrue("the empty week never showed the wait dialog", everShown(R.string.please_wait))
        assertFalse("the wait dialog outlived the refused start", showing(R.string.please_wait))
        assertFalse("the dialog outlived the refused start", main.isShowing)
    }

    @Test
    fun `a save whose insert fails shows the failure toast and no success dialog`() {
        registerMediaProvider(inserts = null)

        save()

        assertSaveFailed()
        assertEquals("a delete ran with no row inserted", emptyList<Uri>(), shadowOf(resolver).deletedUris)
    }

    @Test
    fun `a save whose image cannot be written deletes the row and shows the failure toast`() {
        val uri = insertedUri()
        // compress does not throw on a failing stream; it returns false.
        shadowOf(resolver).registerOutputStreamSupplier(uri) {
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("write failed")
            }
        }

        save()

        assertSaveFailed()
        assertEquals("the unwritten row was not deleted", listOf(uri), shadowOf(resolver).deletedUris)
    }

    @Test
    fun `a save whose stream fails to close deletes the row and shows the failure toast`() {
        val uri = insertedUri()
        shadowOf(resolver).registerOutputStreamSupplier(uri) {
            object : OutputStream() {
                override fun write(b: Int) = Unit
                override fun close() = throw IOException("close failed")
            }
        }

        save()

        assertSaveFailed()
        assertEquals("the unwritten row was not deleted", listOf(uri), shadowOf(resolver).deletedUris)
    }

    @Test
    fun `a save whose stream cannot be opened deletes the row and shows the failure toast`() {
        val uri = insertedUri()
        // The 1-arg openOutputStream turns FileNotFoundException and SecurityException into a dummy
        // sink, so the open failure has to be another exception to reach the code.
        shadowOf(resolver).registerOutputStreamSupplier(uri) { throw IllegalStateException("open failed") }

        save()

        assertSaveFailed()
        assertEquals("the unopened row was not deleted", listOf(uri), shadowOf(resolver).deletedUris)
    }

    @Test
    fun `a save with no output stream deletes the row and shows the failure toast`() {
        val uri = Uri.parse("${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/$INSERT_ID")
        // openAssetFile returning null makes the real ContentResolver.openOutputStream return null.
        registerMediaProvider(inserts = uri)
        assertEquals("the provider route did not yield a null stream", null, resolver.openOutputStream(uri))

        save()

        assertSaveFailed()
        assertTrue("the unwritten row was not deleted", uri in shadowOf(resolver).deletedUris)
    }

    @Test
    fun `a successful save shows the success dialog, no failure toast, and deletes nothing`() {
        save()

        assertTrue("the Share/Open dialog never showed", showing(R.string.timetable_generate_success_title))
        assertFalse("the progress dialog is still showing", showing(R.string.timetable_generate_progress_title))
        assertEquals("a successful save showed a toast", null, ShadowToast.getTextOfLatestToast())
        assertEquals("a successful save deleted a row", emptyList<Uri>(), shadowOf(resolver).deletedUris)
    }

    @Test
    fun `a drawing exception reports the failure and leaves no dialog behind`() {
        val uncaught = drawingThrows(IllegalStateException("draw"))

        assertEquals("the drawing failure escaped the coroutine", emptyList<Throwable>(), uncaught)
        verify(exactly = 1) { Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888) }
        assertSaveFailed()
    }

    @Test
    fun `running out of memory while drawing reports the failure and leaves no dialog behind`() {
        val uncaught = drawingThrows(OutOfMemoryError("draw"))

        assertEquals("the out-of-memory error escaped the coroutine", emptyList<Throwable>(), uncaught)
        verify(exactly = 1) { Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888) }
        assertSaveFailed()
    }

    @Test
    fun `when its own sync has finished and the queue drains on an empty week, it says the week has no lessons`() {
        val (dlg, main) = saveEmptyNextWeek()
        ownTaskStarted()

        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)
        assertTrue("its own sync finishing ended the wait before the queue drained", showing(R.string.please_wait))
        assertEquals("a Finished alone said something", null, ShadowToast.getTextOfLatestToast())

        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        awaitDone(dlg)

        assertFalse("the wait dialog outlived the queue", showing(R.string.please_wait))
        assertTrue("the drain closed the dialog", main.isShowing)
        assertEquals(
            "the empty week was not reported",
            activity.getString(R.string.timetable_generate_no_lessons),
            ShadowToast.getTextOfLatestToast(),
        )
        // The Save, the rebuild after its own sync, and one read after the drain.
        verifyNextWeekReads(3)
        assertFalse("an empty week generated an image", everShown(R.string.timetable_generate_progress_title))
    }

    @Test
    fun `a queue that drains before its own sync started says nothing`() {
        val (dlg, main) = saveEmptyNextWeek()

        // Another task's Finished, then a drain: the dialog's task was dropped before it ran, as a
        // task posted into a closing service is.
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        awaitDone(dlg)
        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        awaitDone(dlg)

        assertEquals("a drain with its own sync never run said something", null, ShadowToast.getTextOfLatestToast())
        assertFalse("the wait dialog outlived the queue", showing(R.string.please_wait))
        assertTrue("the drain closed the dialog", main.isShowing)
        verifyNextWeekReads(2)
    }

    @Test
    fun `a queue that drains after its own sync started but never finished says nothing`() {
        val (dlg, main) = saveEmptyNextWeek()
        // Started, then cancelled or killed: no Finished for this profile follows.
        ownTaskStarted()

        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        awaitDone(dlg)

        assertEquals("a drain after a cut-off sync said something", null, ShadowToast.getTextOfLatestToast())
        assertFalse("the wait dialog outlived the queue", showing(R.string.please_wait))
        assertTrue("the drain closed the dialog", main.isShowing)
        verifyNextWeekReads(1)
    }

    @Test
    fun `a rebuild that resumes after an unproven drain says nothing`() {
        val (dlg, main) = saveEmptyNextWeek()
        shadowOf(activity.application).clearStartedServices()

        armRead()
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        idle()
        assertTrue("the rebuild's read never started", readEntered.await(5, SECONDS))
        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        idle()
        release()
        awaitDone(dlg)

        assertEquals("a rebuild after an unproven drain said something", null, ShadowToast.getTextOfLatestToast())
        assertFalse("the rebuild showed another wait dialog", showing(R.string.please_wait))
        assertFalse("the rebuild started another sync", syncStarted())
        assertTrue("the rebuild closed the dialog", main.isShowing)
        verifyNextWeekReads(2)
    }

    @Test
    fun `a week that arrives before the queue drains is generated once and says nothing`() {
        val (dlg, _) = saveEmptyNextWeek()
        ownTaskStarted()

        // Its own sync's rebuild is still reading when the queue drains, so both reads see the week.
        armRead()
        dlg.onApiTaskFinishedEvent(ApiTaskFinishedEvent(PROFILE_ID))
        idle()
        assertTrue("the rebuild's read never started", readEntered.await(5, SECONDS))
        lessons = listOf(lesson(on = nextWeekStart()))
        dlg.onApiTaskAllFinishedEvent(ApiTaskAllFinishedEvent())
        idle()
        release()
        awaitDone(dlg)

        assertEquals(
            "the week was not generated exactly once",
            1,
            ShadowDialog.getShownDialogs().count { titleOf(it) == activity.getString(R.string.timetable_generate_success_title) },
        )
        assertEquals("a filled week said something", null, ShadowToast.getTextOfLatestToast())
        verifyNextWeekReads(3)
    }

    @Test
    fun `Share grants the target read access to the saved image`() {
        // Read as the dialog hands it over: the framework's start path would add the grant itself
        // (Intent.migrateExtraStreamToClipData, unless implicitUriGrantsRestrictedForSendAction is
        // on), and checking after it could not tell the dialog's flag from the platform's.
        val recording = Robolectric.buildActivity(RecordingActivity::class.java).setup().get()
        activity = recording
        val uri = insertedUri()
        save()
        val success = ShadowDialog.getShownDialogs()
            .single { it.isShowing && titleOf(it) == activity.getString(R.string.timetable_generate_success_title) }
            as AlertDialog

        success.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertEquals("Share did not open a chooser", Intent.ACTION_CHOOSER, recording.startedAction)
        assertEquals("the chooser's target is not a send", Intent.ACTION_SEND, recording.targetAction)
        assertEquals("the send does not carry the saved image", uri, recording.targetStream)
        assertTrue(
            "the send does not grant read access to the image",
            recording.targetFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    /** Opens the dialog, saves next week while it is empty, and returns once the wait is up. */
    private fun saveEmptyNextWeek(): Pair<GenerateBlockTimetableDialog, AlertDialog> {
        val (dlg, main) = open()
        main.findViewById<RadioGroup>(R.id.weekSelectionRadioGroup)!!.check(R.id.withChangesNextWeekRadio)
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
        assertTrue("the empty week did not show the wait dialog", showing(R.string.please_wait))
        return dlg to main
    }

    /** Does what `ApiService.runTask` does to the dialog's own task when it starts running it. */
    private fun ownTaskStarted() {
        EventBus.getDefault().getStickyEvent(EdziennikTask::class.java)!!.taskId = 1
    }

    private fun nextWeekStart(): Date = Week.getWeekEnd().clone().stepForward(0, 0, 1)

    /** Exactly [n] reads, every one of them spanning the whole of next week. */
    private fun verifyNextWeekReads(n: Int) {
        val start = nextWeekStart()
        val end = start.clone().stepForward(0, 0, 6)
        verify(exactly = n) {
            dao.getBetweenDatesNow(
                match { it.stringY_m_d == start.stringY_m_d },
                match { it.stringY_m_d == end.stringY_m_d },
            )
        }
        verify(exactly = n) { dao.getBetweenDatesNow(any(), any()) }
    }

    /**
     * Makes the image's own bitmap allocation throw [t], saves a one-lesson week, and returns what
     * reached the main thread's uncaught handler. Only the call with exactly the image's dimensions is
     * stubbed, so a view's drawing cache still gets a real bitmap.
     */
    private fun drawingThrows(t: Throwable): List<Throwable> {
        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888) } throws t
        val uncaught = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        thread.setUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            save()
        } finally {
            thread.uncaughtExceptionHandler = previous
        }
        return uncaught
    }

    /** Answers `insert` with [inserts], every stream open with null, and every delete with 1. */
    private class MediaProvider(private val inserts: Uri?) : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = inserts
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 1
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = null
    }

    private val resolver get() = activity.applicationContext.contentResolver

    /** Routes the save's `insert`, `openOutputStream` and `delete` to a [MediaProvider]. */
    private fun registerMediaProvider(inserts: Uri?) {
        val provider = MediaProvider(inserts)
        // Attached, or the real resolver's stream open dereferences a null context.
        provider.attachInfo(activity.application, ProviderInfo().apply { authority = "media"; exported = true })
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    /** Makes the next unprovided `insert` return the uri this returns; the shadow pre-increments. */
    private fun insertedUri(): Uri {
        shadowOf(resolver).setNextDatabaseIdForInserts(INSERT_ID - 1)
        return Uri.parse("${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/$INSERT_ID")
    }

    /** Opens the dialog on a week with a lesson, taps Save and waits for the generation to finish. */
    private fun save() {
        lessons = listOf(lesson())
        val (dlg, main) = open()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitDone(dlg)
    }

    private fun assertSaveFailed() {
        assertFalse("a failed save showed the success dialog", showing(R.string.timetable_generate_success_title))
        assertFalse("the progress dialog is still showing", showing(R.string.timetable_generate_progress_title))
        assertEquals(
            "a failed save showed no failure toast",
            activity.getString(R.string.timetable_generate_save_failed),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    /** Refuses a foreground-service start, as Android 12+ does from a background process. */
    class RefusingActivity : AppCompatActivity() {
        override fun startForegroundService(service: Intent?): ComponentName? =
            throw IllegalStateException("refused")
    }

    /**
     * Records the chooser it is asked to start, and its target's action, stream and flags, at the
     * moment of the call: the framework's start path mutates the target in place. Starts nothing.
     */
    class RecordingActivity : AppCompatActivity() {
        var startedAction: String? = null
        var targetAction: String? = null
        var targetStream: Uri? = null
        var targetFlags = 0

        @Suppress("DEPRECATION")
        override fun startActivity(intent: Intent?) {
            startedAction = intent?.action
            val target = intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            targetAction = target?.action
            targetStream = target?.getParcelableExtra(Intent.EXTRA_STREAM)
            targetFlags = target?.flags ?: 0
        }
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
        armRead()
        main.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue("the read never started", readEntered.await(5, SECONDS))
    }

    /** Makes the next read block in the DAO until [release]; [readEntered] counts down when it does. */
    private fun armRead() {
        readEntered = CountDownLatch(1)
        gate = CountDownLatch(1)
    }

    /** Lets a read held by [armRead] return. */
    private fun release() {
        gate?.countDown()
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

    private fun error(profileId: Int?) =
        ApiTaskErrorEvent(ApiError("test", 1).apply { this.profileId = profileId })

    private fun syncStarted() = shadowOf(activity.application).nextStartedService != null

    private fun lesson(on: Date = Week.getWeekStart()) = LessonFull(PROFILE_ID, 1).apply {
        type = Lesson.TYPE_NORMAL
        date = on
        startTime = Time(8, 0, 0)
        endTime = Time(8, 45, 0)
        subjectName = "Matematyka"
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val PROFILE_ID = 9299
        const val INSERT_ID = 4321
        /** The image for [lesson]'s 8:00-8:45 week: 70 + 5 * (285 + 15) - 15, plus the 20 px margin. */
        const val IMAGE_WIDTH = 1575
        /** 60 + 45 min * 3 + 40, plus the 30 px margin; no profile name. */
        const val IMAGE_HEIGHT = 265
    }
}
