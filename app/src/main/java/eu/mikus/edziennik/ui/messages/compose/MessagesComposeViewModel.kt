/*
 * Copyright (c) Mikolaj Olszewski 2026-7-31.
 */
package eu.mikus.edziennik.ui.messages.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.DAY
import eu.mikus.edziennik.utils.managers.MessageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the NON-body state of the Compose message editor: the recipient suggestion list, the
 * selected recipients, the recipient query and the subject. The rich-text body lives in the
 * AndroidView bridge, NOT here.
 *
 * Ports MessagesComposeFragment.getRecipientList/updateRecipientList and the
 * MessagesComposeChipCreator recipient rules (duplicate guard, type-group expansion).
 */
class MessagesComposeViewModel(
    private val loadTeachersFromDb: suspend () -> List<Teacher>,
    private val syncRecipientsIfStale: () -> Boolean,
    private val typeName: (Int) -> String,
    val greeting: MessageManager.GreetingConfig,
) : ViewModel() {

    private val _teachers = MutableStateFlow<List<Teacher>>(emptyList())

    /** The full suggestion list: real teachers (sorted) + the synthetic type-group entries. */
    val teachers: StateFlow<List<Teacher>> = _teachers.asStateFlow()

    private val _selectedRecipients = MutableStateFlow<List<Teacher>>(emptyList())
    val selectedRecipients: StateFlow<List<Teacher>> = _selectedRecipients.asStateFlow()

    private val _recipientQuery = MutableStateFlow("")
    val recipientQuery: StateFlow<String> = _recipientQuery.asStateFlow()

    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()

    private val _isRecipientListReady = MutableStateFlow(false)
    val isRecipientListReady: StateFlow<Boolean> = _isRecipientListReady.asStateFlow()

    private val _draftMessageId = MutableStateFlow<Long?>(null)
    val draftMessageId: StateFlow<Long?> = _draftMessageId.asStateFlow()

    private val _duplicateRecipientEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The user picked an already-selected teacher - the host shows the legacy toast. */
    val duplicateRecipientEvents: SharedFlow<Unit> = _duplicateRecipientEvents

    /** Read by the host for the save-draft-on-leave check (legacy onBeforeNavigate). */
    var changedRecipients = false
        private set
    var changedSubject = false
        private set

    /**
     * Starts loading the recipient list. Returns true if a recipient-list sync was enqueued
     * instead of a DB read (legacy getRecipientList) - the host then shows its "downloading"
     * snackbar and waits for [setTeachers]; false if the list is being read from the DB.
     */
    fun loadTeachers(): Boolean {
        if (syncRecipientsIfStale())
            return true
        viewModelScope.launch {
            _teachers.value = buildTeacherList(loadTeachersFromDb())
            _isRecipientListReady.value = true
        }
        return false
    }

    /**
     * The RecipientListGetEvent refresh path. Re-applies [buildTeacherList] - otherwise the
     * type-group categories would disappear after a recipient sync.
     */
    fun setTeachers(raw: List<Teacher>) {
        _teachers.value = buildTeacherList(raw)
        _isRecipientListReady.value = true
    }

    /** The one transform used by both the initial load and the event refresh (legacy updateRecipientList). */
    private fun buildTeacherList(raw: List<Teacher>): List<Teacher> =
        raw.sortedBy { it.fullName } + Teacher.types.map { Teacher(-1, -it.toLong(), typeName(it), "") }

    fun suggestions(query: String?): List<Teacher> = rankRecipients(_teachers.value, query)

    fun addRecipient(teacher: Teacher) {
        // a synthetic type-group entry is never a recipient - it only opens the category picker
        if (teacher.id in CATEGORY_IDS)
            return
        if (_selectedRecipients.value.any { it.id == teacher.id }) {
            _duplicateRecipientEvents.tryEmit(Unit)
            return
        }
        _selectedRecipients.value = _selectedRecipients.value + teacher
        changedRecipients = true
        _recipientQuery.value = ""
    }

    fun removeRecipient(teacher: Teacher) {
        _selectedRecipients.value = _selectedRecipients.value.filter { it.id != teacher.id }
        changedRecipients = true
    }

    /** The real teachers of [type], ordered as the legacy category dialog ordered them. */
    fun categoryMembers(type: Int): List<Teacher> {
        val members = _teachers.value.filter { it.id !in CATEGORY_IDS && it.isType(type) }
        return if (type in SORT_BY_DESCRIPTION_TYPES)
            members.sortedBy { it.typeDescription }
        else
            members
    }

    fun toggleCategoryMember(teacher: Teacher, checked: Boolean) {
        _selectedRecipients.value = if (checked) {
            if (_selectedRecipients.value.any { it.id == teacher.id })
                _selectedRecipients.value
            else
                _selectedRecipients.value + teacher
        } else {
            _selectedRecipients.value.filter { it.id != teacher.id }
        }
        changedRecipients = true
    }

    /**
     * The legacy Nacho field rejected `[\n;:_ ]`. A native query field must allow typing
     * "Anna Kowalska", so SPACE is deliberately kept - only newline, semicolon, colon and
     * underscore are stripped. As in the legacy, any recipient-field edit marks the form dirty.
     */
    fun onQueryChange(q: String) {
        _recipientQuery.value = q.replace(ILLEGAL_QUERY_CHARS, "")
        changedRecipients = true
    }

    fun onSubjectChange(s: String) {
        _subject.value = s
        changedSubject = true
    }

    /** Pre-fill from a reply/forward/draft payload - not "dirty" afterwards, as in the legacy. */
    fun applyInitial(initial: MessageManager.InitialCompose) {
        _selectedRecipients.value = initial.recipients
        _subject.value = initial.subject ?: ""
        _draftMessageId.value = initial.draftMessageId
        changedRecipients = false
        changedSubject = false
    }

    /** Legacy saveDraft resets the changed flags. */
    fun markSaved() {
        changedRecipients = false
        changedSubject = false
    }

    companion object {
        /** The id range of the synthetic type-group entries (-Teacher.TYPE_*). */
        private val CATEGORY_IDS = -24L..0L
        private val SORT_BY_DESCRIPTION_TYPES = listOf(
            Teacher.TYPE_PARENTS_COUNCIL,
            Teacher.TYPE_EDUCATOR,
            Teacher.TYPE_STUDENT,
        )
        private val ILLEGAL_QUERY_CHARS = Regex("[\n;:_]")
    }

    class Factory(
        private val app: App,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val ui = app.profile.config.ui
            return MessagesComposeViewModel(
                loadTeachersFromDb = {
                    withContext(Dispatchers.IO) {
                        app.db.teacherDao().getAllNow(App.profileId).filter { it.loginId != null }
                    }
                },
                syncRecipientsIfStale = {
                    val stale = app.data.messagesConfig.syncRecipientList &&
                        System.currentTimeMillis() - app.profile.lastReceiversSync > 1 * DAY * 1000
                    if (stale)
                        EdziennikTask.recipientListGet(App.profileId).enqueue(context)
                    stale
                },
                typeName = { Teacher.typeName(context, it) },
                greeting = MessageManager.GreetingConfig(
                    onCompose = ui.messagesGreetingOnCompose,
                    onReply = ui.messagesGreetingOnReply,
                    onForward = ui.messagesGreetingOnForward,
                    text = ui.messagesGreetingText
                        ?: context.getString(R.string.messages_config_greeting_default, app.profile.accountOwnerName),
                ),
            ) as T
        }
    }
}
