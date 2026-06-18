/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Host-side swipe-refresh coordinator, extracted from the Phase-0 Announcements bridge (its 2nd use
 * is Behaviour). Refresh is enabled only while the list is scrolled to the very top. The refresh
 * target is supplied as a [setRefreshEnabled] sink (e.g. `b.refreshLayout::setEnabled`) so the
 * helper never hardcodes a binding/Activity or reaches across an ownership boundary.
 */
@Composable
fun SwipeRefreshScrollBridge(
    listState: LazyListState,
    setRefreshEnabled: (Boolean) -> Unit,
) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .collect { atTop -> setRefreshEnabled(atTop) }
    }
}
