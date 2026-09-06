/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.single

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.ui.messages.MessageEnrich
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How long a body fetch may be pending before the manual "mark as read" escape hatch appears.
 * Long enough that an ordinary open never shows it, short enough that a stuck message is recoverable
 * without the user wondering whether anything is wrong.
 */
private const val STUCK_AFTER_MS = 5_000L

class MessageReadViewModel(
    messageId: Long,
    source: (Long) -> Flow<MessageFull?>,
    teachers: () -> Flow<List<Teacher>>,
    private val accountName: () -> String,
    private val fetchMessage: (MessageFull) -> Unit,
    private val needsReadStatus: () -> Boolean,
    private val onMarkSeen: (MessageFull) -> Unit,
    private val onStar: suspend (MessageFull, Boolean) -> Unit,
    private val onDelete: suspend (MessageFull) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val stuckAfterMs: Long = STUCK_AFTER_MS,
) : ViewModel() {

    private var fetched = false

    /**
     * Set [stuckAfterMs] after the fetch is fired. Gating the manual escape hatch on it keeps the
     * button out of an ordinary open, where the body lands in well under a second and the automatic
     * seen-write follows: offering "mark as read" there would be noise on every message.
     *
     * A timer rather than a failure signal on purpose. The screen has no error channel by design -
     * its migration to Compose dropped EventBus - and a timer also covers the case an error signal
     * cannot: a fetch that never comes back at all.
     */
    @Volatile
    private var fetchStale = false

    // markedSeen and current are written from the flow's dispatcher and read from the UI thread in markSeen().
    @Volatile
    private var markedSeen = false
    @Volatile
    private var current: MessageFull? = null

    private val _canMarkSeen = MutableStateFlow(false)

    /**
     * Whether offering a manual "mark as read" is meaningful: the message exists, is still unread and
     * the automatic seen-write has not fired. It is the only way out for a message whose body can never
     * arrive (deleted server-side), because [MessageReadUiState.Loading] never turns into Content there.
     */
    val canMarkSeen: StateFlow<Boolean> = _canMarkSeen.asStateFlow()

    val uiState: StateFlow<MessageReadUiState> =
        combine(source(messageId), teachers()) { msg, ts -> msg?.also { enrich(it, ts) } }
            .onEach { msg -> runSideEffects(msg) }
            .map { msg -> classify(msg) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageReadUiState.Loading)

    /** Manual escape hatch for a message stuck unread - see [canMarkSeen]. */
    fun markSeen() {
        val message = current ?: return
        if (markedSeen || message.seen) return
        markedSeen = true
        _canMarkSeen.value = false
        viewModelScope.launch(dispatcher) { onMarkSeen(message) }
    }

    fun setStarred(message: MessageFull, starred: Boolean) {
        viewModelScope.launch(dispatcher) { onStar(message, starred) }
    }

    fun delete(message: MessageFull) {
        viewModelScope.launch(dispatcher) { onDelete(message) }
    }

    /** Ports MessageManager.getMessage recipient/sender/readByEveryone enrichment (MessageManager.kt:86-114). */
    private fun enrich(message: MessageFull, teachers: List<Teacher>) {
        MessageEnrich.enrichRecipients(message, teachers, accountName())
        if (message.isSent) {
            if (message.recipients?.any { it.readDate < 1 } == true) message.readByEveryone = false
            if (message.senderName == null) message.senderName = accountName()
        }
    }

    /** Pure: NotFound / Loading (a fetch is pending or body still missing) / Content. */
    private fun classify(message: MessageFull?): MessageReadUiState = when {
        message == null -> MessageReadUiState.NotFound
        needsFetch(message) -> MessageReadUiState.Loading
        else -> MessageReadUiState.Content(message)
    }

    private fun needsFetch(message: MessageFull): Boolean =
        message.body == null ||
            (needsReadStatus() && (((message.isReceived || message.isDeleted) && !message.seen) || message.attachmentIds == null)) ||
            (!needsReadStatus() && !message.readByEveryone)

    /** Side effects hoisted out of [classify]: fire the network fetch once, and the local seen-write once. */
    private fun runSideEffects(message: MessageFull?) {
        current = message
        if (message == null) {
            _canMarkSeen.value = false
            return
        }
        if (!fetched && needsFetch(message)) {
            fetched = true
            fetchMessage(message)
            viewModelScope.launch(dispatcher) {
                delay(stuckAfterMs)
                fetchStale = true
                current?.let { _canMarkSeen.value = !markedSeen && !it.seen }
            }
        }
        if (!markedSeen && message.body != null && !message.seen) {
            markedSeen = true
            onMarkSeen(message)
        }
        _canMarkSeen.value = fetchStale && !markedSeen && !message.seen
    }

    class Factory(
        private val messageId: Long,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MessageReadViewModel(
                messageId = messageId,
                source = { id -> App.db.messageDao().getById(App.profileId, id).asFlow() },
                teachers = { flow { emit(App.db.teacherDao().getAllNow(App.profileId)) } },
                accountName = { App.profile.accountName ?: App.profile.studentNameLong },
                fetchMessage = { EdziennikTask.messageGet(App.profileId, it).enqueue(appContext) },
                needsReadStatus = { App.data.messagesConfig.needsReadStatus },
                onMarkSeen = { App.db.metadataDao().setSeen(App.profileId, it, true) },
                onStar = { m, s -> m.isStarred = s; withContext(Dispatchers.Default) { App.db.messageDao().replace(m) } },
                onDelete = { m -> m.type = Message.TYPE_DELETED; withContext(Dispatchers.Default) { App.db.messageDao().replace(m) } },
            ) as T
    }
}
