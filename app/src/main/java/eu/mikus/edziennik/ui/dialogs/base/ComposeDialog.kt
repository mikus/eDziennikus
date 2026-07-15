/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.dialogs.base

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import eu.mikus.edziennik.ui.compose.setAppThemeContent

/**
 * A [BaseDialog] whose body is Compose: reuses the whole BaseDialog machinery (title, Material
 * buttons + suspend click handlers, isCancelable, onShow/onDismiss listeners, isFinishing guard,
 * CoroutineScope) but renders a [ComposeView] via [setAppThemeContent] instead of a ViewBinding.
 * Callers keep their imperative `.show()` contract.
 */
abstract class ComposeDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ViewDialog<ComposeView>(activity, onShowListener, onDismissListener) {

    final override fun getRootView(): ComposeView =
        ComposeView(activity).apply { setAppThemeContent { Content() } }

    /** The dialog body. May use remember/LaunchedEffect + call `dialog.dismiss()`. */
    @Composable
    protected abstract fun Content()

    // ViewDialog leaves onShow() abstract; nothing async needed at show-time (Content owns its own effects).
    override suspend fun onShow() = Unit
}
