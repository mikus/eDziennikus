/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Message
import eu.mikus.edziennik.data.db.full.MessageFull
import kotlinx.coroutines.launch

private fun tabTitleRes(type: Int): Int = when (type) {
    Message.TYPE_RECEIVED -> R.string.messages_tab_received
    Message.TYPE_SENT -> R.string.messages_tab_sent
    Message.TYPE_DELETED -> R.string.messages_tab_deleted
    else -> R.string.messages_tab_draft
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    state: MessagesUiState,
    onQueryChange: (String) -> Unit,
    onMessageClick: (MessageFull) -> Unit,
    onStarClick: (MessageFull) -> Unit,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    isRefreshing: Boolean,
    onRefresh: (tabType: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        MessagesUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is MessagesUiState.Content -> {
            val pageCount = state.tabs.size
            val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, pageCount - 1)) { pageCount }
            val scope = rememberCoroutineScope()
            // key() per tab so the remember-in-a-loop stays slot-stable even if the tab set ever varies
            val listStates = state.tabs.map { tab -> key(tab.type) { rememberLazyListState() } }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
            }

            Column(modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.messages_search)) },
                )
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    state.tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = pagerState.currentPage == i,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text = { Text(stringResource(tabTitleRes(tab.type))) },
                        )
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    val tab = state.tabs[page]
                    if (tab.type in Message.TYPE_RECEIVED..Message.TYPE_SENT) {
                        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { onRefresh(tab.type) }) {
                            MessageList(tab.items, state.query, listStates[page], onMessageClick, onStarClick)
                        }
                    } else {
                        MessageList(tab.items, state.query, listStates[page], onMessageClick, onStarClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageList(
    items: List<MessageFull>,
    query: String,
    listState: LazyListState,
    onMessageClick: (MessageFull) -> Unit,
    onStarClick: (MessageFull) -> Unit,
) {
    if (items.isEmpty()) {
        val msg = if (query.isBlank()) R.string.messages_no_data else R.string.messages_search_no_results
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(msg), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
        if (query.isNotBlank()) {
            item(key = "count") {
                Text(
                    text = stringResource(R.string.messages_search_results, items.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(items, key = { it.id }) { message ->
            MessageRow(message, query, onMessageClick, onStarClick)
        }
    }
}
