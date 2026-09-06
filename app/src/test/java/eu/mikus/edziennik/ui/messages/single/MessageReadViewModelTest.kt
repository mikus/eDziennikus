/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.single

import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.data.db.full.MessageRecipientFull
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageReadViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun message(
        type: Int = Message.TYPE_RECEIVED,
        body: String? = "hello",
        seen: Boolean = true,
        attachmentIds: MutableList<Long>? = mutableListOf(),
        recipients: MutableList<MessageRecipientFull>? = null,
    ): MessageFull = mockk(relaxed = true) {
        every { this@mockk.type } returns type
        every { isReceived } returns (type == Message.TYPE_RECEIVED)
        every { isDeleted } returns (type == Message.TYPE_DELETED)
        every { isSent } returns (type == Message.TYPE_SENT)
        every { this@mockk.body } returns body
        every { this@mockk.seen } returns seen
        every { this@mockk.attachmentIds } returns attachmentIds
        every { readByEveryone } returns true   // real MessageFull default is true (@Ignore @Transient); relaxed mock would give false
        every { profileId } returns 1
        every { this@mockk.recipients } returns recipients
    }

    private fun vm(
        msg: MessageFull?,
        needsReadStatus: Boolean = false,
        fetched: MutableList<MessageFull> = mutableListOf(),
        markedSeen: MutableList<MessageFull> = mutableListOf(),
        teachers: List<Teacher> = emptyList(),
        accountName: String = "Me",
    ) = MessageReadViewModel(
        messageId = 5L,
        source = { flowOf(msg) },
        teachers = { flowOf(teachers) },
        accountName = { accountName },
        fetchMessage = { fetched.add(it) },
        needsReadStatus = { needsReadStatus },
        onMarkSeen = { markedSeen.add(it) },
        onStar = { _, _ -> },
        onDelete = { },
        dispatcher = dispatcher,
    )

    @Test
    fun `null source yields NotFound`() = runTest(dispatcher) {
        val model = vm(null)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(MessageReadUiState.NotFound, model.uiState.value)
        job.cancel()
    }

    @Test
    fun `complete seen message yields Content without fetching`() = runTest(dispatcher) {
        val fetched = mutableListOf<MessageFull>()
        val model = vm(message(body = "hi", seen = true), fetched = fetched)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is MessageReadUiState.Content)
        assertTrue(fetched.isEmpty())
        job.cancel()
    }

    @Test
    fun `missing body stays Loading and fetches once`() = runTest(dispatcher) {
        val fetched = mutableListOf<MessageFull>()
        val model = vm(message(body = null), fetched = fetched)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(MessageReadUiState.Loading, model.uiState.value)
        assertEquals(1, fetched.size)
        job.cancel()
    }

    @Test
    fun `enriches recipients on a sent message`() = runTest(dispatcher) {
        val recipients = mutableListOf(MessageRecipientFull(profileId = 1, id = 100L, messageId = 5L, readDate = 0L))
        val sent = message(type = Message.TYPE_SENT, body = "hi", seen = true, recipients = recipients)
        val model = vm(sent, teachers = listOf(Teacher(1, 100L, "Anna", "Kowalska")))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("Anna Kowalska", recipients[0].fullName)
        job.cancel()
    }

    @Test
    fun `marks seen once when body present and unseen`() = runTest(dispatcher) {
        val markedSeen = mutableListOf<MessageFull>()
        val model = vm(message(body = "hi", seen = false), markedSeen = markedSeen)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, markedSeen.size)
        job.cancel()
    }

    @Test
    fun `a body-less unread message offers the manual mark-as-read`() = runTest(dispatcher) {
        // The stuck case: the server dropped the message, so its body can never arrive and the
        // body-gated automatic seen-write can never fire. Loading is the only state it ever reaches.
        val markedSeen = mutableListOf<MessageFull>()
        val model = vm(message(body = null, seen = false), markedSeen = markedSeen)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(MessageReadUiState.Loading, model.uiState.value)
        assertTrue(markedSeen.isEmpty())
        assertTrue(model.canMarkSeen.value)
        job.cancel()
    }

    @Test
    fun `the manual mark-as-read is withheld until the fetch looks stuck`() = runTest(dispatcher) {
        // An ordinary open resolves in well under a second, so the escape hatch must not flash up on
        // every message - it appears only once the fetch has been pending long enough to look stuck.
        val model = vm(message(body = null, seen = false))
        val job = launch { model.uiState.collect {} }
        runCurrent()
        assertEquals(MessageReadUiState.Loading, model.uiState.value)
        assertFalse(model.canMarkSeen.value, "offered before the stuck window elapsed")
        advanceUntilIdle()
        assertTrue(model.canMarkSeen.value, "not offered after the stuck window elapsed")
        job.cancel()
    }

    @Test
    fun `markSeen writes seen once for a body-less message`() = runTest(dispatcher) {
        val markedSeen = mutableListOf<MessageFull>()
        val model = vm(message(body = null, seen = false), markedSeen = markedSeen)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()

        model.markSeen()
        advanceUntilIdle()
        assertEquals(1, markedSeen.size)
        assertFalse(model.canMarkSeen.value)

        model.markSeen()   // the source never re-emits (setSeen writes metadata, not messages)
        advanceUntilIdle()
        assertEquals(1, markedSeen.size)
        job.cancel()
    }

    @Test
    fun `an automatically seen message does not offer the manual mark-as-read`() = runTest(dispatcher) {
        val markedSeen = mutableListOf<MessageFull>()
        val model = vm(message(body = "hi", seen = false), markedSeen = markedSeen)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, markedSeen.size)
        assertFalse(model.canMarkSeen.value)
        job.cancel()
    }

    @Test
    fun `needsReadStatus received-unseen stays Loading and fetches`() = runTest(dispatcher) {
        val fetched = mutableListOf<MessageFull>()
        val model = vm(
            message(type = Message.TYPE_RECEIVED, body = "hi", seen = false),
            needsReadStatus = true,
            fetched = fetched,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(MessageReadUiState.Loading, model.uiState.value)
        assertEquals(1, fetched.size)
        job.cancel()
    }

    @Test
    fun `fetch fires once across re-emissions, then Content when body arrives`() = runTest(dispatcher) {
        val fetched = mutableListOf<MessageFull>()
        val src = MutableStateFlow<MessageFull?>(message(body = null))
        val model = MessageReadViewModel(
            messageId = 5L,
            source = { src },
            teachers = { flowOf(emptyList()) },
            accountName = { "Me" },
            fetchMessage = { fetched.add(it) },
            needsReadStatus = { false },
            onMarkSeen = { },
            onStar = { _, _ -> },
            onDelete = { },
            dispatcher = dispatcher,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(MessageReadUiState.Loading, model.uiState.value)
        assertEquals(1, fetched.size)

        src.value = message(body = "now here", seen = true)   // fetch wrote the body -> source re-emits
        advanceUntilIdle()
        assertTrue(model.uiState.value is MessageReadUiState.Content)
        assertEquals(1, fetched.size)   // still once across both emissions
        job.cancel()
    }
}
