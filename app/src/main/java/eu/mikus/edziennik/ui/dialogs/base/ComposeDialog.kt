/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.dialogs.base

import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import eu.mikus.edziennik.ui.compose.theme.AppTheme
import eu.mikus.edziennik.ui.compose.theme.appColorScheme

/**
 * A [BaseDialog] whose body is Compose: reuses the whole BaseDialog machinery (title, Material
 * buttons + suspend click handlers, isCancelable, onShow/onDismiss listeners, isFinishing guard,
 * CoroutineScope) but renders a [ComposeView] instead of a ViewBinding. Callers keep their
 * imperative `.show()` contract.
 */
abstract class ComposeDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ViewDialog<ComposeView>(activity, onShowListener, onDismissListener) {

    final override fun getRootView(): ComposeView =
        ComposeView(activity).apply {
            // Dispose the composition when the dialog's view detaches from its window (on dismiss);
            // the ViewTree owners the recomposer needs are installed on the decorView in onBeforeShow.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // `this@ComposeDialog` is required: inside apply {} the receiver is the ComposeView, and
            // AbstractComposeView also declares Content() — an unqualified Content() would call the
            // view's own content renderer and recurse infinitely (StackOverflowError).
            setContent { AppTheme { this@ComposeDialog.Content() } }
        }

    // A MaterialAlertDialog's window installs no ViewTree owners, so Compose's window-recomposer
    // lookup (createLifecycleAwareWindowRecomposer, on attach) crashes with "ViewTreeLifecycleOwner
    // not found". That lookup starts at the window root and walks *up*, so the owners must sit on the
    // decorView, not the descendant ComposeView. onBeforeShow runs after create() but before show()
    // (i.e. before the content attaches), which is exactly when the decorView exists and can be tagged.
    final override suspend fun onBeforeShow(): Boolean {
        dialog.window?.let { window ->
            window.decorView.setViewTreeLifecycleOwner(activity)
            window.decorView.setViewTreeViewModelStoreOwner(activity)
            window.decorView.setViewTreeSavedStateRegistryOwner(activity)
            // Paint the whole dialog window (incl. the AlertDialog button panel) with the SAME M3
            // background the Compose content uses (AppTheme's Surface = colorScheme.background). Without
            // this the M2 button panel reads as a separate gray footer under the near-black content.
            val density = activity.resources.displayMetrics.density
            val bg = MaterialShapeDrawable(
                ShapeAppearanceModel.builder().setAllCornerSizes(28f * density).build(),
            ).apply { fillColor = ColorStateList.valueOf(appColorScheme(activity).background.toArgb()) }
            window.setBackgroundDrawable(InsetDrawable(bg, (16 * density).toInt()))
        }
        return true
    }

    /** The dialog body. May use remember/LaunchedEffect + call `dialog.dismiss()`. */
    @Composable
    protected abstract fun Content()

    // ViewDialog leaves onShow() abstract; nothing async needed at show-time (Content owns its own effects).
    override suspend fun onShow() = Unit
}
