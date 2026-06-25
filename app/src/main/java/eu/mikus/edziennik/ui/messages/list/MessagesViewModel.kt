/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.ui.messages.MessageEnrich
import eu.mikus.edziennik.ui.search.SearchMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(
    private val source: (Int) -> Flow<List<MessageFull>>,
    teachers: () -> Flow<List<Teacher>>,
    private val onStar: suspend (MessageFull, Boolean) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Enriched lists per tab, recomputed only on a source/teachers change (NOT on query). Mutates each
    // message in place (filterNotes + recipient fullName fill); re-enrich is idempotent. Faithful to the
    // legacy list fragment. NOTE: the inner combine(Iterable<Flow>) emits only after EVERY tab's source has
    // emitted at least once — a stalled source stalls all tabs (the ratified observe-all-4 behavior).
    private val enriched: Flow<List<List<MessageFull>>> =
        combine(combine(TYPES.map { source(it) }) { it.toList() }, teachers()) { perType, ts ->
            perType.map { list ->
                list.onEach { msg ->
                    msg.filterNotes()
                    MessageEnrich.enrichRecipients(msg, ts, accountName = null)
                }
            }
        }

    val uiState: StateFlow<MessagesUiState> =
        combine(enriched, query) { lists, q ->
            MessagesUiState.Content(q, TYPES.mapIndexed { i, type -> MessageTab(type, rank(lists[i], q)) })
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState.Loading)

    fun setQuery(q: String) {
        query.value = q
    }

    fun setStarred(message: MessageFull, starred: Boolean) {
        viewModelScope.launch(dispatcher) { onStar(message, starred) }
    }

    /** Filter (by [q]) + rank one tab. Blank [q] keeps all. Sort mirrors MessageFull.compareTo:
     *  relevance best-first, then starred-first, then newest-first. */
    private fun rank(messages: List<MessageFull>, q: String): List<MessageFull> {
        val ranked: List<Pair<MessageFull, Int>> =
            if (q.isBlank()) {
                messages.map { it to SearchMatch.NO_MATCH }
            } else {
                messages.mapNotNull { m ->
                    val relevance = SearchMatch.relevance(m.searchKeywords, q)
                    if (relevance == SearchMatch.NO_MATCH) null else m to relevance
                }
            }
        return ranked.sortedWith(
            compareBy<Pair<MessageFull, Int>> { it.second }
                .thenByDescending { it.first.isStarred }
                .thenByDescending { it.first.addedDate }
        ).map { it.first }
    }

    companion object {
        private val TYPES = listOf(
            Message.TYPE_RECEIVED, Message.TYPE_SENT, Message.TYPE_DELETED, Message.TYPE_DRAFT,
        )
    }

    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MessagesViewModel(
                source = { type -> App.db.messageDao().getAllByType(App.profileId, type).asFlow() },
                teachers = { flow { emit(App.db.teacherDao().getAllNow(App.profileId)) } },
                onStar = { m, s ->
                    m.isStarred = s
                    withContext(Dispatchers.Default) { App.db.messageDao().replace(m) }
                },
            ) as T
    }
}
