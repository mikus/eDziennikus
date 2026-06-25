/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.list

import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.data.db.full.MessageRecipientFull
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun msg(
        msgId: Long,
        starred: Boolean = false,
        added: Long = 0L,
        keywords: List<List<String?>> = listOf(listOf("msg$msgId")),
        recipients: MutableList<MessageRecipientFull>? = null,
    ): MessageFull = mockk(relaxed = true) {
        every { id } returns msgId
        every { isStarred } returns starred
        every { addedDate } returns added
        every { searchKeywords } returns keywords
        every { notes } returns mutableListOf()
        every { this@mockk.recipients } returns recipients
    }

    private fun vm(
        byType: Map<Int, List<MessageFull>>,
        onStar: suspend (MessageFull, Boolean) -> Unit = { _, _ -> },
    ) = MessagesViewModel(
        source = { type -> flowOf(byType[type] ?: emptyList()) },
        teachers = { flowOf(emptyList()) },
        onStar = onStar,
        dispatcher = dispatcher,
    )

    private fun content(model: MessagesViewModel): MessagesUiState.Content =
        model.uiState.value as MessagesUiState.Content

    @Test
    fun `routes each source to its tab in Received,Sent,Deleted,Draft order`() = runTest(dispatcher) {
        val r = msg(1); val s = msg(2); val d = msg(3); val w = msg(4)
        val model = vm(mapOf(
            Message.TYPE_RECEIVED to listOf(r),
            Message.TYPE_SENT to listOf(s),
            Message.TYPE_DELETED to listOf(d),
            Message.TYPE_DRAFT to listOf(w),
        ))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val c = content(model)
        assertEquals(listOf(Message.TYPE_RECEIVED, Message.TYPE_SENT, Message.TYPE_DELETED, Message.TYPE_DRAFT), c.tabs.map { it.type })
        assertEquals(listOf(1L), c.tabs[0].items.map { it.id })
        assertEquals(listOf(2L), c.tabs[1].items.map { it.id })
        assertEquals(listOf(3L), c.tabs[2].items.map { it.id })
        assertEquals(listOf(4L), c.tabs[3].items.map { it.id })
        job.cancel()
    }

    @Test
    fun `blank query orders starred-first then newest-first`() = runTest(dispatcher) {
        val older = msg(1, starred = false, added = 100)
        val newer = msg(2, starred = false, added = 200)
        val starred = msg(3, starred = true, added = 50)
        val model = vm(mapOf(Message.TYPE_RECEIVED to listOf(older, newer, starred)))
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(3L, 2L, 1L), content(model).tabs[0].items.map { it.id })
        job.cancel()
    }

    @Test
    fun `search filters to matches and drops non-matches`() = runTest(dispatcher) {
        val hit = msg(1, keywords = listOf(listOf("Matematyka")))
        val miss = msg(2, keywords = listOf(listOf("Fizyka")))
        val model = vm(mapOf(Message.TYPE_RECEIVED to listOf(hit, miss)))
        val job = launch { model.uiState.collect {} }
        model.setQuery("mat")
        advanceUntilIdle()
        assertEquals(listOf(1L), content(model).tabs[0].items.map { it.id })
        job.cancel()
    }

    @Test
    fun `enriches recipient fullName from teachers`() = runTest(dispatcher) {
        val recipients = mutableListOf(MessageRecipientFull(profileId = 1, id = 100L, messageId = 1L))
        val sent = mockk<MessageFull>(relaxed = true) {
            every { id } returns 1L; every { isStarred } returns false; every { addedDate } returns 0L
            every { searchKeywords } returns listOf(listOf("x")); every { notes } returns mutableListOf()
            every { profileId } returns 1; every { this@mockk.recipients } returns recipients
        }
        val model = MessagesViewModel(
            source = { type -> if (type == Message.TYPE_SENT) flowOf(listOf(sent)) else flowOf(emptyList()) },
            teachers = { flowOf(listOf(eu.mikus.edziennik.data.db.entity.Teacher(1, 100L, "Anna", "Kowalska"))) },
            onStar = { _, _ -> },
            dispatcher = dispatcher,
        )
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("Anna Kowalska", recipients[0].fullName)
        job.cancel()
    }

    @Test
    fun `setStarred invokes onStar`() = runTest(dispatcher) {
        val starred = mutableListOf<Pair<Long, Boolean>>()
        val m = msg(1, starred = false)
        val model = vm(mapOf(Message.TYPE_RECEIVED to listOf(m))) { msg, s -> starred.add(msg.id to s) }
        model.setStarred(m, true)
        advanceUntilIdle()
        assertEquals(listOf(1L to true), starred)
    }

    @Test
    fun `initial state is Loading then Content`() = runTest(dispatcher) {
        val model = vm(mapOf(Message.TYPE_RECEIVED to listOf(msg(1))))
        assertEquals(MessagesUiState.Loading, model.uiState.value)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is MessagesUiState.Content)
        job.cancel()
    }
}
