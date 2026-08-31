/*
 * Copyright (c) Mikolaj Olszewski 2026-8-7.
 */

package eu.mikus.edziennik.ui.messages.compose

import eu.mikus.edziennik.R
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MessagesComposeActionsTest {

    private fun actions(hasDraft: Boolean) = messagesComposeActions(
        hasDraft = hasDraft,
        onSend = {}, onSaveDraft = {}, onDiscard = {}, onConfig = {},
    )

    @Test
    fun `without a draft, offers send, save and config`() {
        assertEquals(
            listOf(
                R.string.messages_compose_send_long,
                R.string.messages_compose_save_draft,
                R.string.menu_messages_config,
            ),
            actions(hasDraft = false).map { it.titleRes },
        )
    }

    /** Pins the old `addItemAt(2, …)` coupling as an assertion. */
    @Test
    fun `with a draft, the discard row sits at index 2`() {
        val rows = actions(hasDraft = true)
        assertEquals(
            listOf(
                R.string.messages_compose_send_long,
                R.string.messages_compose_save_draft,
                R.string.messages_compose_discard_draft,
                R.string.menu_messages_config,
            ),
            rows.map { it.titleRes },
        )
        assertEquals(R.string.messages_compose_discard_draft, rows[2].titleRes)
    }

    @Test
    fun `the discard row appears exactly once however often we re-declare`() {
        repeat(3) {
            val discards = actions(hasDraft = true).count {
                it.titleRes == R.string.messages_compose_discard_draft
            }
            assertEquals(1, discards)
        }
    }

    @Test
    fun `config carries the separator, and it is the only one`() {
        val rows = actions(hasDraft = true)
        assertEquals(listOf(R.string.menu_messages_config), rows.filter { it.separatorBefore }.map { it.titleRes })
        assertTrue(rows.last().separatorBefore)
    }

    @Test
    fun `each row invokes its own callback`() {
        val fired = mutableListOf<String>()
        val rows = messagesComposeActions(
            hasDraft = true,
            onSend = { fired += "send" },
            onSaveDraft = { fired += "save" },
            onDiscard = { fired += "discard" },
            onConfig = { fired += "config" },
        )
        rows.forEach { it.onClick() }
        assertEquals(listOf("send", "save", "discard", "config"), fired)
    }
}
