/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.ui.messages.compose

import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.utils.managers.MessageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesComposeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val greeting = MessageManager.GreetingConfig(
        onCompose = true,
        onReply = false,
        onForward = false,
        text = "greeting",
    )

    private fun teacher(
        id: Long,
        name: String,
        surname: String,
        type: Int? = null,
        description: String? = null,
    ) = Teacher(1, id, name, surname).also { t ->
        type?.let { t.setTeacherType(it) }
        t.typeDescription = description
    }

    private val kowalska = teacher(100L, "Anna", "Kowalska", Teacher.TYPE_TEACHER)
    private val nowak = teacher(101L, "Jan", "Nowak", Teacher.TYPE_TEACHER)
    private val kowal = teacher(102L, "Kowal", "Zenon", Teacher.TYPE_TEACHER)

    private fun vm(
        db: List<Teacher> = emptyList(),
        sync: () -> Boolean = { false },
    ) = MessagesComposeViewModel(
        loadTeachersFromDb = { db },
        syncRecipientsIfStale = sync,
        typeName = { "Type$it" },
        greeting = greeting,
    )

    private fun categoryIds(model: MessagesComposeViewModel) =
        model.teachers.value.takeLast(Teacher.types.size).map { it.id }

    @Test
    fun `loadTeachers sorts by fullName and appends the type-group entries`() = runTest(dispatcher) {
        val model = vm(db = listOf(nowak, kowalska))
        assertFalse(model.loadTeachers())
        advanceUntilIdle()

        assertEquals(2 + Teacher.types.size, model.teachers.value.size)
        assertEquals(listOf("Anna Kowalska", "Jan Nowak"), model.teachers.value.take(2).map { it.fullName })
        assertEquals(Teacher.types.map { -it.toLong() }, categoryIds(model))
        assertTrue(categoryIds(model).all { it in -24L..0L })
        assertTrue(model.isRecipientListReady.value)
    }

    @Test
    fun `a kicked-off sync skips the DB read and leaves the list not ready`() = runTest(dispatcher) {
        val model = vm(db = listOf(nowak), sync = { true })
        assertTrue(model.loadTeachers())
        advanceUntilIdle()

        assertEquals(emptyList(), model.teachers.value)
        assertFalse(model.isRecipientListReady.value)
    }

    @Test
    fun `setTeachers applies the same transform as the initial load`() = runTest(dispatcher) {
        val model = vm()
        model.setTeachers(listOf(nowak, kowalska))

        assertEquals(2 + Teacher.types.size, model.teachers.value.size)
        assertEquals(listOf("Anna Kowalska", "Jan Nowak"), model.teachers.value.take(2).map { it.fullName })
        assertEquals(Teacher.types.map { -it.toLong() }, categoryIds(model))
        assertTrue(model.isRecipientListReady.value)
    }

    @Test
    fun `addRecipient adds once and reports a duplicate on the second add`() = runTest(dispatcher) {
        val model = vm()
        val events = mutableListOf<Unit>()
        val collector = launch { model.duplicateRecipientEvents.collect { events += it } }
        advanceUntilIdle()

        model.addRecipient(kowalska)
        assertEquals(listOf(100L), model.selectedRecipients.value.map { it.id })
        assertTrue(model.changedRecipients)

        model.addRecipient(kowalska)
        advanceUntilIdle()
        assertEquals(1, model.selectedRecipients.value.size)
        assertEquals(1, events.size)

        collector.cancel()
    }

    @Test
    fun `addRecipient clears the query`() = runTest(dispatcher) {
        val model = vm()
        model.onQueryChange("Kowal")
        model.addRecipient(kowalska)
        assertEquals("", model.recipientQuery.value)
    }

    @Test
    fun `addRecipient ignores a synthetic type-group entry`() = runTest(dispatcher) {
        val model = vm()
        model.addRecipient(teacher(-Teacher.TYPE_STUDENT.toLong(), "Type12", ""))
        model.addRecipient(teacher(-Teacher.TYPE_TEACHER.toLong(), "Type0", ""))
        model.addRecipient(teacher(-24L, "Type24", ""))

        assertEquals(emptyList(), model.selectedRecipients.value)
        assertFalse(model.changedRecipients)
    }

    @Test
    fun `removeRecipient removes by id`() = runTest(dispatcher) {
        val model = vm()
        model.addRecipient(kowalska)
        model.addRecipient(nowak)
        model.removeRecipient(kowalska)

        assertEquals(listOf(101L), model.selectedRecipients.value.map { it.id })
    }

    @Test
    fun `categoryMembers returns only real teachers of that type`() = runTest(dispatcher) {
        val pedagogue = teacher(200L, "Ewa", "Pedagog", Teacher.TYPE_PEDAGOGUE)
        val model = vm()
        model.setTeachers(listOf(kowalska, nowak, pedagogue))

        assertEquals(listOf(100L, 101L), model.categoryMembers(Teacher.TYPE_TEACHER).map { it.id })
        assertEquals(listOf(200L), model.categoryMembers(Teacher.TYPE_PEDAGOGUE).map { it.id })
        assertEquals(emptyList(), model.categoryMembers(Teacher.TYPE_LIBRARIAN))
    }

    @Test
    fun `categoryMembers sorts the description-bearing types by typeDescription`() = runTest(dispatcher) {
        // fullName order (Ala, Bob) is the INVERSE of the description order, so the two branches differ
        val bob = teacher(300L, "Bob", "Uczen", Teacher.TYPE_STUDENT, description = "1a")
        val ala = teacher(301L, "Ala", "Uczen", Teacher.TYPE_STUDENT, description = "2b")
        val model = vm()
        model.setTeachers(listOf(bob, ala))

        assertEquals(listOf(300L, 301L), model.categoryMembers(Teacher.TYPE_STUDENT).map { it.id })
    }

    @Test
    fun `categoryMembers keeps the source order for the other types`() = runTest(dispatcher) {
        val bob = teacher(300L, "Bob", "Nauczyciel", Teacher.TYPE_TEACHER, description = "1a")
        val ala = teacher(301L, "Ala", "Nauczyciel", Teacher.TYPE_TEACHER, description = "2b")
        val model = vm()
        model.setTeachers(listOf(bob, ala))

        // TYPE_TEACHER is not one of the sorted-by-description types - fullName order survives
        assertEquals(listOf(301L, 300L), model.categoryMembers(Teacher.TYPE_TEACHER).map { it.id })
    }

    @Test
    fun `toggleCategoryMember adds when checked and removes when unchecked`() = runTest(dispatcher) {
        val model = vm()
        model.toggleCategoryMember(kowalska, true)
        assertEquals(listOf(100L), model.selectedRecipients.value.map { it.id })

        model.toggleCategoryMember(kowalska, true)
        assertEquals(1, model.selectedRecipients.value.size)

        model.toggleCategoryMember(kowalska, false)
        assertEquals(emptyList(), model.selectedRecipients.value)
        assertTrue(model.changedRecipients)
    }

    /**
     * Seeds a VM with the three TYPE_TEACHER fixtures visible and [selected] already chosen, with
     * both dirty flags reset - i.e. the reply/forward/draft entry state, which is where an untouched
     * OK must stay non-dirty.
     */
    private fun vmWithSelection(
        visible: List<Teacher> = listOf(kowalska, nowak, kowal),
        selected: List<Teacher> = listOf(kowalska),
    ) = vm().also { model ->
        model.setTeachers(visible)
        model.applyInitial(
            MessageManager.InitialCompose(
                recipients = selected,
                subject = null,
                body = null,
                draftMessageId = null,
                isDraft = false,
            )
        )
    }

    @Test
    fun `commitCategorySelection adds the newly-checked members`() = runTest(dispatcher) {
        val model = vmWithSelection(selected = emptyList())

        model.commitCategorySelection(shownIds = setOf(100L, 101L, 102L), checkedIds = setOf(100L, 102L))

        assertEquals(listOf(100L, 102L), model.selectedRecipients.value.map { it.id })
        assertTrue(model.changedRecipients)
    }

    @Test
    fun `commitCategorySelection removes members that were shown but left unchecked`() = runTest(dispatcher) {
        val model = vmWithSelection(selected = listOf(kowalska, nowak))

        model.commitCategorySelection(shownIds = setOf(100L, 101L, 102L), checkedIds = setOf(101L))

        assertEquals(listOf(101L), model.selectedRecipients.value.map { it.id })
        assertTrue(model.changedRecipients)
    }

    @Test
    fun `commitCategorySelection leaves recipients outside shownIds untouched`() = runTest(dispatcher) {
        val pedagogue = teacher(200L, "Ewa", "Pedagog", Teacher.TYPE_PEDAGOGUE)
        val model = vmWithSelection(
            visible = listOf(kowalska, nowak, pedagogue),
            selected = listOf(pedagogue, kowalska),
        )

        // the Teacher category was shown; the pedagogue was never on screen
        model.commitCategorySelection(shownIds = setOf(100L, 101L), checkedIds = emptySet())

        assertEquals(listOf(200L), model.selectedRecipients.value.map { it.id })
    }

    @Test
    fun `commitCategorySelection does not duplicate an already-selected member`() = runTest(dispatcher) {
        val model = vmWithSelection(selected = listOf(kowalska))

        model.commitCategorySelection(shownIds = setOf(100L, 101L), checkedIds = setOf(100L, 101L))

        assertEquals(listOf(100L, 101L), model.selectedRecipients.value.map { it.id })
    }

    @Test
    fun `commitCategorySelection with no checked ids removes every shown member`() = runTest(dispatcher) {
        val model = vmWithSelection(selected = listOf(kowalska, nowak, kowal))

        model.commitCategorySelection(shownIds = setOf(100L, 101L, 102L), checkedIds = emptySet())

        assertEquals(emptyList(), model.selectedRecipients.value)
        assertTrue(model.changedRecipients)
    }

    @Test
    fun `commitCategorySelection with no change keeps the form clean`() = runTest(dispatcher) {
        // NON-DEGENERATE fixture: the roster contains UNSELECTED members (nowak, kowal), so
        // `shownIds - checkedIds` is non-empty and only the intersection with the current selection
        // makes this a no-op. An all-shown-are-selected fixture would pass even with that bug.
        val model = vmWithSelection(selected = listOf(kowalska))

        model.commitCategorySelection(shownIds = setOf(100L, 101L, 102L), checkedIds = setOf(100L))

        assertEquals(listOf(100L), model.selectedRecipients.value.map { it.id })
        assertFalse(model.changedRecipients)
    }

    @Test
    fun `commitCategorySelection does not clear the query and reports no duplicate`() = runTest(dispatcher) {
        val model = vmWithSelection(selected = listOf(kowalska))
        model.onQueryChange("Kowal")
        val events = mutableListOf<Unit>()
        val collector = launch { model.duplicateRecipientEvents.collect { events += it } }
        advanceUntilIdle()

        // kowalska is checked AND already selected - addRecipient would have fired the toast here
        model.commitCategorySelection(shownIds = setOf(100L, 101L), checkedIds = setOf(100L, 101L))
        advanceUntilIdle()

        assertEquals("Kowal", model.recipientQuery.value)
        assertEquals(emptyList(), events)

        collector.cancel()
    }

    @Test
    fun `commitCategorySelection ignores unknown and synthetic ids`() = runTest(dispatcher) {
        val model = vmWithSelection(selected = emptyList())

        model.commitCategorySelection(
            shownIds = setOf(100L),
            // 999 is not in the teacher list; 0 and -12 are synthetic type-group ids
            checkedIds = setOf(100L, 999L, 0L, -12L),
        )

        assertEquals(listOf(100L), model.selectedRecipients.value.map { it.id })
    }

    @Test
    fun `suggestions ranks via rankRecipients`() = runTest(dispatcher) {
        val model = vm()
        model.setTeachers(listOf(kowalska, nowak, kowal))

        // "Kowal" starts the whole name of 102 (weight 1) and a word of 100 (weight 2)
        assertEquals(listOf(102L, 100L), model.suggestions("Kowal").map { it.id })
    }

    @Test
    fun `suggestions with a null query returns only the type-group entries`() = runTest(dispatcher) {
        val model = vm()
        model.setTeachers(listOf(kowalska, nowak))

        assertEquals(Teacher.types.map { -it.toLong() }, model.suggestions(null).map { it.id })
    }

    @Test
    fun `onSubjectChange sets the subject and marks it changed`() = runTest(dispatcher) {
        val model = vm()
        model.onSubjectChange("Temat")

        assertEquals("Temat", model.subject.value)
        assertTrue(model.changedSubject)
        assertFalse(model.changedRecipients)
    }

    @Test
    fun `applyInitial pre-fills and resets both changed flags`() = runTest(dispatcher) {
        val model = vm()
        model.onQueryChange("x")
        model.onSubjectChange("y")

        model.applyInitial(
            MessageManager.InitialCompose(
                recipients = listOf(kowalska),
                subject = "Re: Temat",
                body = null,
                draftMessageId = 42L,
                isDraft = true,
            )
        )

        assertEquals(listOf(100L), model.selectedRecipients.value.map { it.id })
        assertEquals("Re: Temat", model.subject.value)
        assertEquals(42L, model.draftMessageId.value)
        assertFalse(model.changedRecipients)
        assertFalse(model.changedSubject)
    }

    @Test
    fun `applyInitial with a null subject clears the subject`() = runTest(dispatcher) {
        val model = vm()
        model.onSubjectChange("y")
        model.applyInitial(
            MessageManager.InitialCompose(
                recipients = emptyList(),
                subject = null,
                body = null,
                draftMessageId = null,
                isDraft = false,
            )
        )

        assertEquals("", model.subject.value)
        assertEquals(null, model.draftMessageId.value)
    }

    @Test
    fun `markSaved resets both changed flags`() = runTest(dispatcher) {
        val model = vm()
        model.onQueryChange("x")
        model.onSubjectChange("y")
        model.markSaved()

        assertFalse(model.changedRecipients)
        assertFalse(model.changedSubject)
    }

    @Test
    fun `onQueryChange strips the illegal characters but keeps spaces`() = runTest(dispatcher) {
        val model = vm()
        model.onQueryChange("a;b_c")
        assertEquals("abc", model.recipientQuery.value)

        model.onQueryChange("a\nb:c")
        assertEquals("abc", model.recipientQuery.value)

        model.onQueryChange("Anna Kowalska")
        assertEquals("Anna Kowalska", model.recipientQuery.value)
        assertTrue(model.changedRecipients)
    }
}
