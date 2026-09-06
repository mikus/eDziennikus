/*
 * Copyright (c) Mikolaj Olszewski 2026-9-6.
 */

package eu.mikus.edziennik.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's unread marker. `#F44336` is the established convention here — the grades tree, the
 * message detail header and the drawer badge all use it — so it is deliberately a fixed colour
 * rather than a theme role, which would make "unread" mean a different colour per theme.
 *
 * [visible] draws the dot or leaves the slot empty while still occupying it, so rows with and
 * without the marker stay aligned in a list.
 */
@Composable
fun UnreadDot(modifier: Modifier = Modifier, visible: Boolean = true) {
    Box(modifier.size(8.dp)) {
        if (visible) Canvas(Modifier.fillMaxSize()) { drawCircle(UnreadColor) }
    }
}

private val UnreadColor = Color(0xFFF44336)
