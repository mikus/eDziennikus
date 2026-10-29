/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.APP_DB_VERSION
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.data.db.DatabaseChoice
import eu.mikus.edziennik.ui.base.BuildInvalidActivity
import eu.mikus.edziennik.ui.login.LoginActivity
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * What the user sees when `AppDb` has parked a database from a newer build on a sidecar.
 *
 * The sidecar is blank, so `App.profileId` is `0` on it, and `MainActivity` answers that by
 * launching `LoginActivity` and finishing. Without a branch ahead of that check the user lands on
 * a login form for an app that looks freshly installed, and the screen that would explain it is
 * behind them: a dismissible notice started from `MainActivity` would sit under the login wizard
 * with nothing left to go back to. So the notice replaces the wizard, and these tests assert that
 * it does, and that nothing else is started next to it.
 *
 * Both paths under test `finish()` before `MainActivity` sets up its Compose content, so
 * Robolectric never has to host a composition (see the note in `CLAUDE.md`).
 *
 * `AppDb.choice` is written directly, which is what its `internal set` is for, and reset on both
 * sides of every test: the real `App` boots before each one and may record a choice of its own,
 * and a value left by another test must not be able to pass or fail this one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FutureDatabaseNoticeTest {

    private val newer = DatabaseChoice.Newer(SIDECAR, storedVersion = STORED_VERSION)
    private val unreadable = DatabaseChoice.Unreadable(SIDECAR)

    @Before
    fun resetState() {
        AppDb.choice = null
        // validateBuild shows its debug notice once per device and records that in config, which
        // outlives the test that did it. Cleared, every launch that reaches validateBuild shows a
        // second activity, so a branch placed after it cannot hide behind test order.
        App.config.validation = null
    }

    @After
    fun resetChoice() {
        AppDb.choice = null
    }

    @Test
    fun `a parked database starts the notice and finishes MainActivity`() {
        for (choice in listOf(newer, unreadable)) {
            AppDb.choice = choice

            val activity = launchMainActivity().get()
            val started = startedBy(activity)

            val notice = started.singleOrNull()
            assertNotNull("$choice must start exactly one activity, got $started", notice)
            assertEquals(BuildInvalidActivity::class.java.name, notice!!.component?.className)
            assertTrue("$choice must finish MainActivity", activity.isFinishing)
            // isCritical hides the close button; a dismissible notice would leave MainActivity
            // gone with nothing to go back to
            assertTrue("$choice must be critical", notice.hasExtra("isCritical"))
            assertTrue("$choice must be critical", notice.getBooleanExtra("isCritical", false))
            // BuildInvalidActivity tints with the int it is given, so a resource id would paint
            // a colour nobody chose
            assertEquals(
                "$choice must pass a resolved colour, not a resource id",
                activity.getColor(R.color.md_orange_500),
                notice.getIntExtra("color", 0),
            )
        }
    }

    @Test
    fun `a parked database never starts LoginActivity`() {
        for (choice in listOf(newer, unreadable)) {
            AppDb.choice = choice

            val started = startedBy(launchMainActivity().get())

            assertFalse(
                "$choice must not start LoginActivity, got $started",
                started.any { it.component?.className == LoginActivity::class.java.name },
            )
        }
    }

    @Test
    fun `a second launch while parked parks again`() {
        AppDb.choice = newer

        val first = launchMainActivity()
        assertEquals(1, startedBy(first.get()).size)
        first.destroy()

        // nothing about the first launch may have used the parked state up
        assertEquals(newer, AppDb.choice)

        val second = launchMainActivity().get()
        val started = startedBy(second)

        assertEquals(
            BuildInvalidActivity::class.java.name,
            started.singleOrNull()?.component?.className,
        )
        assertTrue(second.isFinishing)
    }

    @Test
    fun `a normal database reaches the ordinary empty-profile path`() {
        AppDb.choice = DatabaseChoice.Current(REAL_NAME)
        assertEquals("the test needs a profile-less app", 0, App.profileId)

        val activity = launchMainActivity().get()
        val started = startedBy(activity)

        // validateBuild may start its own, non-critical BuildInvalidActivity in a debug build;
        // what matters is that the login wizard is reached and the database notice is not
        assertTrue(
            "a normal database must reach LoginActivity, got $started",
            started.any { it.component?.className == LoginActivity::class.java.name },
        )
        assertTrue(activity.isFinishing)
        assertTrue(
            "a normal database must not carry the parked notice, got $started",
            started.mapNotNull { it.getStringExtra("message") }.none { it in parkedMessages() },
        )
    }

    @Test
    fun `the two parked states carry different messages`() {
        val newerMessage = noticeMessage(newer)
        val unreadableMessage = noticeMessage(unreadable)
        val context = RuntimeEnvironment.getApplication()

        assertNotEquals(newerMessage, unreadableMessage)
        assertEquals(context.getString(R.string.db_newer_version, STORED_VERSION, APP_DB_VERSION), newerMessage)
        assertEquals(context.getString(R.string.db_unreadable), unreadableMessage)
        assertTrue("$newerMessage must say the stored version", newerMessage.contains("$STORED_VERSION"))
        // the guard never read a version in this state, so the message must not claim one
        assertFalse(
            "$unreadableMessage must not claim a version",
            unreadableMessage.any { it.isDigit() },
        )
    }

    @Test
    fun `every language says the stored version for a newer database and none for an unreadable one`() {
        val context = RuntimeEnvironment.getApplication()
        val newerMessages = mutableMapOf<String, String>()
        val unreadableMessages = mutableMapOf<String, String>()
        for (language in listOf("pl", "en", "de")) {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language))
            val localized = context.createConfigurationContext(configuration)

            val newerMessage = localized.getString(R.string.db_newer_version, STORED_VERSION, APP_DB_VERSION)
            val unreadableMessage = localized.getString(R.string.db_unreadable)
            newerMessages[language] = newerMessage
            unreadableMessages[language] = unreadableMessage

            // The only digits in the message are the two numbers, so their order is the order the
            // string places them in: %1$d the stored version, %2$d the one this build supports.
            // A `contains` on either would pass with the two swapped.
            assertEquals(
                "$language: $newerMessage",
                listOf("$STORED_VERSION", "$APP_DB_VERSION"),
                Regex("\\d+").findAll(newerMessage).map { it.value }.toList(),
            )
            assertFalse("$language: $unreadableMessage", unreadableMessage.any { it.isDigit() })
            assertNotEquals(language, newerMessage, unreadableMessage)
        }

        // Everything above also holds for the default Polish text, which is what a missing
        // values-xx key, or a locale that never applied, falls back to. Only three messages that
        // differ from one another show that each language was really looked up.
        assertEquals("one db_newer_version per language: $newerMessages", 3, newerMessages.values.toSet().size)
        assertEquals("one db_unreadable per language: $unreadableMessages", 3, unreadableMessages.values.toSet().size)
    }

    /**
     * Runs `onCreate` only: both tested paths finish before anything past it. The main looper is
     * then let run, because `validateBuild` launches its work onto it: a launch that reached
     * `validateBuild` shows up as a second activity, and the parked path must never have reached it.
     */
    private fun launchMainActivity(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).create()
            .also { shadowOf(Looper.getMainLooper()).idle() }

    /** The `message` extra of the one notice `MainActivity` starts for [choice]. */
    private fun noticeMessage(choice: DatabaseChoice): String {
        AppDb.choice = choice
        val started = startedBy(launchMainActivity().get())
        return started.singleOrNull()?.getStringExtra("message")
            ?: error("$choice must start one notice with a message, got $started")
    }

    /** Every activity [activity] started, in order, so no stray one can hide behind `next`. */
    private fun startedBy(activity: Activity): List<Intent> =
        generateSequence { shadowOf(activity).nextStartedActivity }.toList()

    private fun parkedMessages(): Set<String> {
        val context = RuntimeEnvironment.getApplication()
        return setOf(
            context.getString(R.string.db_newer_version, STORED_VERSION, APP_DB_VERSION),
            context.getString(R.string.db_unreadable),
        )
    }

    private companion object {
        const val SIDECAR = "edziennik-v3.db"
        const val REAL_NAME = "edziennik.db"

        /** Distinctive, so a digit in a message can only have come from the stored version. */
        const val STORED_VERSION = 987
    }
}
