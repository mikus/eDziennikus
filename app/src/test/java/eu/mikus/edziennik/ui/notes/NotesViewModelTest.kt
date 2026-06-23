/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.notes

import eu.mikus.edziennik.data.db.entity.Note
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
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun note(
        noteId: Long,
        type: Note.OwnerType?,
        added: Long,
        keywords: List<List<String?>> = listOf(listOf("note$noteId")),
    ): Note = mockk(relaxed = true) {
        every { id } returns noteId
        every { ownerType } returns type
        every { addedDate } returns added
        every { searchKeywords } returns keywords
    }

    private fun vm(notes: List<Note>) = NotesViewModel(
        source = { flowOf(notes) },
        dispatcher = dispatcher,
    )

    @Test
    fun `empty source yields Empty`() = runTest(dispatcher) {
        val model = vm(emptyList())
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(NotesUiState.Empty, model.uiState.value)
        job.cancel()
    }

    @Test
    fun `blank query groups by owner type, null owner leads with no header, addedDate desc within group`() = runTest(dispatcher) {
        val notes = listOf(
            note(1, null, added = 100),
            note(2, Note.OwnerType.EVENT, added = 200),
            note(3, Note.OwnerType.EVENT, added = 100),
        )
        val model = vm(notes)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is NotesUiState.Content)
        assertEquals(3, state.resultCount)
        assertEquals(NoteRow.Item(notes[0]), state.rows[0])
        assertEquals(NoteRow.Header(Note.OwnerType.EVENT), state.rows[1])
        assertEquals(NoteRow.Item(notes[1]), state.rows[2])
        assertEquals(NoteRow.Item(notes[2]), state.rows[3])
        job.cancel()
    }

    @Test
    fun `query filters to matches, resultCount excludes headers, no orphan headers`() = runTest(dispatcher) {
        val notes = listOf(
            note(1, Note.OwnerType.EVENT, added = 100, keywords = listOf(listOf("Matematyka"))),
            note(2, Note.OwnerType.GRADE, added = 100, keywords = listOf(listOf("Fizyka"))),
        )
        val model = vm(notes)
        val job = launch { model.uiState.collect {} }
        model.setQuery("mat")
        advanceUntilIdle()
        val state = model.uiState.value as NotesUiState.Content
        assertEquals(1, state.resultCount)
        assertEquals(listOf(NoteRow.Header(Note.OwnerType.EVENT), NoteRow.Item(notes[0])), state.rows)
        job.cancel()
    }

    @Test
    fun `query matching nothing yields empty rows`() = runTest(dispatcher) {
        val notes = listOf(note(1, Note.OwnerType.EVENT, added = 100, keywords = listOf(listOf("Matematyka"))))
        val model = vm(notes)
        val job = launch { model.uiState.collect {} }
        model.setQuery("zzz")
        advanceUntilIdle()
        val state = model.uiState.value as NotesUiState.Content
        assertEquals(0, state.resultCount)
        assertTrue(state.rows.isEmpty())
        job.cancel()
    }

    @Test
    fun `unsupported owner type gets no header and does not throw`() = runTest(dispatcher) {
        val notes = listOf(note(1, Note.OwnerType.EVENT_SUBJECT, added = 100))
        val model = vm(notes)
        val job = launch { model.uiState.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value as NotesUiState.Content
        assertEquals(listOf(NoteRow.Item(notes[0])), state.rows)
        job.cancel()
    }
}
