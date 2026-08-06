/*
 * Copyright (c) Mikolaj Olszewski 2026-8-7.
 */

package eu.mikus.edziennik.utils.managers

import android.app.Application
import eu.mikus.edziennik.App
import eu.mikus.edziennik.utils.TextInputKeyboardEdit
import eu.mikus.edziennik.utils.html.BetterHtml
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the draft-body round trip: what [TextStylingManager.getHtmlText] writes into `Message.body`
 * must come back out of [BetterHtml.fromHtml] as the same text.
 *
 * `Html.toHtml` encodes a blank line structurally, as `</p><p>` rather than a `<br>`, but
 * BetterHtml's decoder rewrites `<p` to `<span` (no newline) and `</p>` to `</span><br>` (one), so
 * every run of k >= 2 newlines used to come back as k-1. On the default signature that showed up as
 * the body counter dropping 34 -> 33 after a save-and-reopen; on a real message it collapsed every
 * blank line between paragraphs.
 *
 * Robolectric + JUnit 4 (through the Vintage engine, as in RobolectricSmokeTest) because both
 * directions run on the real `android.text.Html` - `HtmlCompat.toHtml`/`fromHtml` are stubs in the
 * android.jar a plain Jupiter test sees. No Compose rule is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MessageDraftBodyRoundTripTest {

    /** MessagesComposeFragment.draftBody(). */
    private fun draftBody(body: CharSequence): String {
        val edit = TextInputKeyboardEdit(RuntimeEnvironment.getApplication())
        edit.setText(body)
        val config = StylingConfigBase(editText = edit, htmlMode = HtmlMode.ORIGINAL)
        return TextStylingManager(mockk<App>(relaxed = true))
            .getHtmlText(config, htmlMode = HtmlMode.ORIGINAL)
    }

    /**
     * MessageManager.fillFromBundle()'s draft branch. A null context only skips BetterHtml's
     * colour-contrast pass, and none of these bodies carry a colour.
     */
    private fun reopenDraft(html: String): String =
        BetterHtml.fromHtml(context = null, html).toString()

    private fun assertSurvives(body: String) =
        assertEquals(body, reopenDraft(draftBody(body)))

    @Test
    fun `the default greeting survives a save and reopen`() {
        // R.string.messages_config_greeting_default, formatted - the reported 34-char body
        val body = "\n\nZ poważaniem\nStanisław Olszewski"
        assertEquals(34, body.length)
        assertSurvives(body)
    }

    @Test
    fun `blank lines between paragraphs survive a save and reopen`() {
        assertSurvives("Dzień dobry,\n\nTreść wiadomości.\n\nZ poważaniem\nJan Kowalski")
    }

    @Test
    fun `a run of three newlines survives a save and reopen`() {
        assertSurvives("A\n\n\nB")
    }

    @Test
    fun `single newlines survive a save and reopen`() {
        // guard: the fix must not ADD newlines where there was no blank line
        assertSurvives("A\nB\nC")
    }

    @Test
    fun `a second save and reopen changes nothing`() {
        // guard: idempotent, so a draft edited repeatedly does not drift
        val once = reopenDraft(draftBody("A\n\nB"))
        assertEquals(once, reopenDraft(draftBody(once)))
    }
}
