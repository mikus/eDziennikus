/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessagesGreetingTest {
    @Test fun `blank input saves null`() {
        assertNull(messagesGreetingSave(""))
        assertNull(messagesGreetingSave("   \n "))
    }
    @Test fun `non-blank is trimmed and prefixed with two newlines`() {
        assertEquals("\n\nRegards", messagesGreetingSave("Regards"))
        assertEquals("\n\nRegards\nJan", messagesGreetingSave("  Regards\nJan  "))
    }
}
