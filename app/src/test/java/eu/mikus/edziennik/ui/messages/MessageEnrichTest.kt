/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages

import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.data.db.full.MessageRecipientFull
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class MessageEnrichTest {

    private fun recipient(id: Long, profileId: Int = 1, fullName: String? = null): MessageRecipientFull =
        MessageRecipientFull(profileId = profileId, id = id, messageId = 10L).also { it.fullName = fullName }

    private fun message(profileId: Int = 1, recipients: MutableList<MessageRecipientFull>): MessageFull =
        mockk(relaxed = true) {
            every { this@mockk.profileId } returns profileId
            every { this@mockk.recipients } returns recipients
        }

    private val teachers = listOf(
        Teacher(1, 100L, "Anna", "Kowalska"),
        Teacher(1, 101L, "Jan", "Nowak"),
    )

    @Test
    fun `drops cross-profile recipients`() {
        val list = mutableListOf(recipient(100L, profileId = 1), recipient(200L, profileId = 2))
        MessageEnrich.enrichRecipients(message(1, list), teachers, accountName = null)
        assertEquals(listOf(100L), list.map { it.id })
    }

    @Test
    fun `fills fullName from teachers`() {
        val list = mutableListOf(recipient(100L), recipient(101L))
        MessageEnrich.enrichRecipients(message(1, list), teachers, accountName = null)
        assertEquals(listOf("Anna Kowalska", "Jan Nowak"), list.map { it.fullName })
    }

    @Test
    fun `unknown recipient gets empty name`() {
        val list = mutableListOf(recipient(999L))
        MessageEnrich.enrichRecipients(message(1, list), teachers, accountName = null)
        assertEquals("", list[0].fullName)
    }

    @Test
    fun `self recipient -1 maps to accountName when provided, empty when null`() {
        val withAccount = mutableListOf(recipient(-1L))
        MessageEnrich.enrichRecipients(message(1, withAccount), teachers, accountName = "Tomasz Olszewski")
        assertEquals("Tomasz Olszewski", withAccount[0].fullName)

        val withoutAccount = mutableListOf(recipient(-1L))
        MessageEnrich.enrichRecipients(message(1, withoutAccount), teachers, accountName = null)
        assertEquals("", withoutAccount[0].fullName)
    }

    @Test
    fun `preserves an already-set non-null fullName`() {
        val list = mutableListOf(recipient(100L, fullName = "Pre Set"))
        MessageEnrich.enrichRecipients(message(1, list), teachers, accountName = null)
        assertEquals(listOf("Pre Set"), list.map { it.fullName })
    }
}
