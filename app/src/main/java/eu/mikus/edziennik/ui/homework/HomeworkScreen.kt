/*
 * Copyright (c) Mikolaj Olszewski 2026-6-24.
 */

package eu.mikus.edziennik.ui.homework

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.ui.event.EventRow
import kotlinx.coroutines.launch

@Composable
fun HomeworkScreen(
    state: HomeworkUiState,
    onQueryChange: (String) -> Unit,
    onEventClick: (EventFull) -> Unit,
    onEventEditClick: (EventFull) -> Unit,
    onItemSeen: (EventFull) -> Unit,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeworkUiState.Loading ->
            Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        is HomeworkUiState.Content -> {
            val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 1)) { 2 }
            val scope = rememberCoroutineScope()
            val currentListState = rememberLazyListState()
            val pastListState = rememberLazyListState()

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
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.homework_tab_current)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.homework_tab_past)) },
                    )
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    val items = if (page == 0) state.current else state.past
                    val listState = if (page == 0) currentListState else pastListState
                    HomeworkList(items, state.query, listState, onEventClick, onEventEditClick, onItemSeen)
                }
            }
        }
    }
}

@Composable
private fun HomeworkList(
    items: List<HomeworkItem>,
    query: String,
    listState: LazyListState,
    onEventClick: (EventFull) -> Unit,
    onEventEditClick: (EventFull) -> Unit,
    onItemSeen: (EventFull) -> Unit,
) {
    if (items.isEmpty()) {
        val msg = if (query.isBlank()) R.string.homework_no_data else R.string.homework_search_no_results
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(msg), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (query.isNotBlank()) {
            item(key = "count") {
                Text(
                    text = stringResource(R.string.homework_search_results, items.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(items, key = { it.event.id }) { item ->
            EventRow(
                event = item.event,
                unseen = item.unseen,
                query = query,
                onClick = onEventClick,
                onEditClick = onEventEditClick,
                onAppear = onItemSeen,
            )
        }
    }
}
