/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.utils.managers

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.AppDb
import eu.mikus.edziennik.data.db.dao.MessageDao
import eu.mikus.edziennik.data.db.dao.MessageRecipientDao
import eu.mikus.edziennik.data.db.dao.MetadataDao
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.entity.MessageRecipient
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.enums.MetadataType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import org.junit.jupiter.api.Test

/**
 * Pins the data-mapping of the Compose-facing [MessageManager.saveAsDraft] overload.
 * The DB is a MockK stub tree - [App.db] is a plain getter, so a relaxed App mock is enough
 * (no Robolectric, no real Room).
 */
class MessageManagerDraftTest {

    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val recipientDao = mockk<MessageRecipientDao>(relaxed = true)
    private val metadataDao = mockk<MetadataDao>(relaxed = true)

    private lateinit var manager: MessageManager

    private val recipients = listOf(
        Teacher(1, 10L, "Anna", "Kowalska"),
        Teacher(1, 11L, "Jan", "Nowak"),
    )

    @BeforeTest
    fun setUp() {
        val db = mockk<AppDb>(relaxed = true)
        every { db.messageDao() } returns messageDao
        every { db.messageRecipientDao() } returns recipientDao
        every { db.metadataDao() } returns metadataDao

        val app = mockk<App>(relaxed = true)
        every { app.db } returns db

        manager = MessageManager(app)
    }

    @Test
    fun `saves the draft message, its recipients and its metadata`() = runBlocking {
        manager.saveAsDraft(
            profileId = 1,
            messageId = null,
            recipients = recipients,
            subject = "S",
            bodyHtml = "<p>B</p>",
        )

        verify {
            messageDao.replace(match {
                it.profileId == 1 &&
                    it.type == Message.TYPE_DRAFT &&
                    it.subject == "S" &&
                    it.body == "<p>B</p>" &&
                    it.senderId == -1L
            })
        }
        verify {
            recipientDao.addAll(match<List<MessageRecipient>> { list ->
                list.size == 2 && list.map { it.id }.containsAll(listOf(10L, 11L))
            })
        }
        verify {
            metadataDao.add(match<Metadata> {
                it.thingType == MetadataType.MESSAGE && it.seen && it.notified
            })
        }
    }

    @Test
    fun `recipients are linked to the saved message id`() = runBlocking {
        manager.saveAsDraft(
            profileId = 1,
            messageId = 42L,
            recipients = recipients,
            subject = "S",
            bodyHtml = "B",
        )

        verify {
            recipientDao.addAll(match<List<MessageRecipient>> { list ->
                list.all { it.messageId == 42L && it.profileId == 1 }
            })
        }
    }

    @Test
    fun `an existing draft clears its old recipients and keeps its id`() = runBlocking {
        manager.saveAsDraft(
            profileId = 1,
            messageId = 42L,
            recipients = recipients,
            subject = "S",
            bodyHtml = "B",
        )

        verify { recipientDao.clearFor(1, 42L) }
        verify { messageDao.replace(match { it.id == 42L }) }
    }

    @Test
    fun `a new draft does not clear any recipients`() = runBlocking {
        manager.saveAsDraft(
            profileId = 1,
            messageId = null,
            recipients = recipients,
            subject = "S",
            bodyHtml = "B",
        )

        verify(exactly = 0) { recipientDao.clearFor(any(), any()) }
    }
}
