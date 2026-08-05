/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.ui.messages.compose

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class MessageComposeBodyTest {
    @Test fun `reply payload implies the reply subject prefix`() =
        assertEquals(SubjectMode.REPLY, subjectMode("reply"))

    @Test fun `forward payload implies the forward subject prefix`() =
        assertEquals(SubjectMode.FORWARD, subjectMode("forward"))

    @Test fun `absent payload type implies no subject transform`() =
        assertEquals(SubjectMode.NONE, subjectMode(null))

    @Test fun `unknown payload type implies no subject transform`() =
        assertEquals(SubjectMode.NONE, subjectMode("something-else"))

    @Test fun `reply greeting is used when enabled on reply`() =
        assertEquals("G", greetingFor("reply", "G", onReply = true, onForward = false))

    @Test fun `reply greeting is skipped when disabled on reply`() =
        assertNull(greetingFor("reply", "G", onReply = false, onForward = true))

    @Test fun `forward greeting is used when enabled on forward`() =
        assertEquals("G", greetingFor("forward", "G", onReply = false, onForward = true))

    @Test fun `forward greeting is skipped when disabled on forward`() =
        assertNull(greetingFor("forward", "G", onReply = true, onForward = false))

    @Test fun `no payload type never greets, whatever the flags`() =
        assertNull(greetingFor(null, "G", onReply = true, onForward = true))
}
