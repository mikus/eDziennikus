/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-22.
 */

package eu.mikus.edziennik.ui.base

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import eu.mikus.edziennik.R
import eu.mikus.edziennik.compat.getColorFromAttr
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's general-purpose snackbar, shown through the single `SnackbarHostState` that `ShellState`
 * owns instead of through navlib's `CoordinatorLayout` (design §7.11).
 *
 * `MainActivity.snackbar()`/`snackbarDismiss()` and their fragment callers are untouched; the only
 * seam that moved is the one that hands this class somewhere to show - `setCoordinator` became
 * [setHostState].
 *
 * There is no `anchorView` equivalent and none is needed: the old instance was anchored above
 * `navView.bottomBar`, and `Scaffold`'s `snackbarHost` slot already sits above its `bottomBar`.
 */
class MainSnackbar(val activity: AppCompatActivity) {
    companion object {
        private const val TAG = "MainSnackbar"

        /** `snackbar?.duration = 7000`, as the pre-Compose [snackbar] set on every show. */
        private const val DurationMs = 7_000L
    }

    private var hostState: SnackbarHostState? = null

    /** The coroutine currently showing; cancelling it is what dismisses - see [dismiss]. */
    private var job: Job? = null

    /**
     * Takes `ShellState.snackbarHostState` - **the one instance** `Scaffold`'s `snackbarHost`
     * renders. This is a setter rather than a constructor parameter with a default precisely
     * because a throwaway `SnackbarHostState` compiles fine and then shows nothing at all: the
     * caller has to hand over the shell's own, and a setter is what forces it to.
     */
    fun setHostState(hostState: SnackbarHostState) {
        this.hostState = hostState
    }

    /**
     * 7 s, read off `snackbar?.duration = 7000`. [SnackbarDuration] has no millisecond variant, so
     * the timing is ours: show [SnackbarDuration.Indefinite] and let [withTimeoutOrNull] cancel the
     * call, which `showSnackbar`'s own `finally` turns into a dismiss. The nearest stock value,
     * `Long`, is 10 s - so it is not used.
     */
    fun snackbar(text: String, actionText: String? = null, onClick: (() -> Unit)? = null) {
        val host = hostState ?: return
        job?.cancel()
        // MDC's `SnackbarManager` cancelled whatever was on screen when a new snackbar was shown,
        // and both classes shared that one manager - so an error snackbar replaced this one and
        // vice versa. `SnackbarHostState` queues on a mutex instead, so the replace has to be
        // explicit or a "message sent" would sit behind the error host's 15 s.
        host.currentSnackbarData?.dismiss()
        job = activity.lifecycleScope.launch {
            val result = withTimeoutOrNull(DurationMs) {
                host.showSnackbar(
                    message = text,
                    actionLabel = actionText,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed)
                onClick?.invoke()
        }
    }

    /** `snackbar?.dismiss()`: cancelling the showing coroutine clears the host's current data. */
    fun dismiss() {
        job?.cancel()
        job = null
    }
}

/**
 * `Scaffold`'s `snackbarHost` content for **both** app snackbar hosts - this one and
 * `ui/error/ErrorSnackbar.kt` - so the background they had under MDC survives the move (§7.11).
 *
 * It lives in `ui/base` because that is the shared-scaffolding package and the two classes that
 * need it sit in different feature packages; neither owns the `Scaffold`.
 *
 * **`AppScaffold` has to render this in place of the bare `SnackbarHost(state.snackbarHostState)`
 * it renders today.** That one-line swap is all that stands between these three colours and the
 * screen, and it belongs to the task that owns that file, not to this one.
 *
 * All three colours are stated rather than inherited from [Snackbar]'s M3 defaults, which are
 * `inverseSurface`/`inverseOnSurface`/`inversePrimary` - roles `appColorScheme` keeps at their brand
 * Blue values on every theme, and unrelated to what ships today.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Resolved once per context, as `AppBottomBar.barContainerColor()` is: a theme change goes
    // through the Activity-recreate path, so a new theme always brings a new context.
    val (container, message, action) = remember(context) {
        val surface = getColorFromAttr(context, R.attr.colorSurface)
        val onSurface = getColorFromAttr(context, R.attr.colorOnSurface)
        val primary = getColorFromAttr(context, R.attr.colorPrimary)
        Triple(
            // The app's own blend, verbatim from what this file used to pass to
            // `setBackgroundTint`: `colorOnSurface` masked to alpha 0xcf over `colorSurface`.
            Color(ColorUtils.compositeColors(onSurface and 0xcfffffff.toInt(), surface)),
            // MDC's own message colour, which the app never overrode:
            // `Widget.MaterialComponents.Snackbar.TextView` is `?attr/colorSurface` drawn at
            // `material_emphasis_high_type` (0.87) via `android:alpha`.
            Color(surface).copy(alpha = 0.87f),
            // MDC's own action colour: `Widget.MaterialComponents.Button.TextButton.Snackbar` is
            // `?attr/colorPrimary`, which `BaseTransientBottomBar` then layers over the message
            // colour at the MaterialComponents `actionTextColorAlpha` of 0.5 (= alpha 0x80).
            Color(ColorUtils.compositeColors(primary and 0x80ffffff.toInt(), surface)),
        )
    }

    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = container,
            contentColor = message,
            actionColor = action,
            // Inert while the action is a `TextButton` whose own colours win, but it is the same
            // label either way, so it is not left on the M3 default.
            actionContentColor = action,
        )
    }
}
