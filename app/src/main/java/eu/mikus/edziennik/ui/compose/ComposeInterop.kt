/*
 * Copyright (c) Mikolaj Olszewski 2026-6-18.
 */

package eu.mikus.edziennik.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import eu.mikus.edziennik.ui.compose.theme.AppTheme

/**
 * The canonical interop bridge. Sets the two invariants every hosted Compose
 * screen needs — DisposeOnViewTreeLifecycleDestroyed (before setContent) so the
 * composition follows the fragment's viewLifecycleOwner across nav transitions,
 * and the AppTheme wrap — then nothing else.
 */
fun ComposeView.setAppThemeContent(content: @Composable () -> Unit) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { AppTheme(content) }
}
