/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-13.
 */

package eu.mikus.edziennik.ui.error

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.models.ApiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * MainActivity's only user-visible report of an API failure: `onApiTaskErrorEvent` is the sole
 * consumer of the sticky `ApiTaskErrorEvent` that every Librus `data.error(ApiError(...))` produces,
 * and the `R.string.more` action here is the only route to [ErrorDetailsDialog] from that screen.
 *
 * Shown through the single `SnackbarHostState` that `ShellState` owns, rather than through navlib's
 * `CoordinatorLayout` (design §7.11). `MainActivity.error()` and `LabProfileFragment`'s and
 * `MessagesComposeFragment`'s calls into it are untouched.
 *
 * There is no `anchorView` equivalent and none is needed: the old instance was anchored above
 * `navView.bottomBar`, and `Scaffold`'s `snackbarHost` slot already sits above its `bottomBar`.
 *
 * The colours are `ui/base/MainSnackbar.kt`'s `AppSnackbarHost`, which renders every snackbar this
 * host state carries - both classes always shared one background.
 */
class ErrorSnackbar(val activity: AppCompatActivity) {
    companion object {
        private const val TAG = "ErrorSnackbar"

        /** `snackbar?.duration = 15000`, as the pre-Compose [addError] set on every error. */
        private const val DurationMs = 15_000L
    }

    private var hostState: SnackbarHostState? = null

    /** The coroutine currently showing; cancelling it is what dismisses - see [dismiss]. */
    private var job: Job? = null

    /**
     * Every error since the last "more" tap. [ErrorDetailsDialog] renders all of them and its
     * `onBeforeShow` refuses an empty list, which is why [show] clears this *after* handing it over.
     */
    private var errors = mutableListOf<ApiError>()

    /**
     * What the MDC instance was built with, and what it still said if `show()` ever ran before an
     * [addError] - it never does today, both call sites chain the two.
     */
    private var message = activity.getString(R.string.snackbar_error_text)

    /**
     * Takes `ShellState.snackbarHostState` - **the one instance** `Scaffold`'s `snackbarHost`
     * renders. This is a setter rather than a constructor parameter with a default precisely
     * because a throwaway `SnackbarHostState` compiles fine and then shows nothing at all, which
     * for this class means the app reports no API failure at all and nothing anywhere fails.
     */
    fun setHostState(hostState: SnackbarHostState) {
        this.hostState = hostState
    }

    @Deprecated("navlib is gone; call setHostState(ShellState.snackbarHostState) instead.")
    fun setCoordinator(coordinatorLayout: CoordinatorLayout, showAbove: View? = null) {
        // Deliberately empty. `MainActivity.kt:184` still calls it and the shell swap that deletes
        // that line deletes this method with it; until then every error report here is mute, which
        // is only acceptable because the intermediate commits of this phase never ship.
    }

    fun addError(apiError: ApiError): ErrorSnackbar {
        errors.add(apiError)
        message = apiError.getStringReason(activity)
        return this
    }

    /**
     * 15 s, read off `snackbar?.duration = 15000`. [SnackbarDuration] has no millisecond variant,
     * so the timing is ours: show [SnackbarDuration.Indefinite] and let [withTimeoutOrNull] cancel
     * the call, which `showSnackbar`'s own `finally` turns into a dismiss. Neither stock value is
     * close - `Long` is 10 s - so neither is used.
     */
    fun show() {
        val host = hostState ?: return
        val text = message
        job?.cancel()
        // MDC's `SnackbarManager` cancelled whatever was on screen when a new snackbar was shown,
        // and both classes shared that one manager - so this replaced a `MainSnackbar` and vice
        // versa. `SnackbarHostState` queues on a mutex instead, so the replace has to be explicit
        // or an error would sit behind whatever is already up.
        host.currentSnackbarData?.dismiss()
        job = activity.lifecycleScope.launch {
            val result = withTimeoutOrNull(DurationMs) {
                host.showSnackbar(
                    message = text,
                    actionLabel = activity.getString(R.string.more),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                // Show first, clear after, and clear by *reassigning*: the dialog keeps the list it
                // was handed, so `errors.clear()` would empty it under the dialog and
                // `onBeforeShow`'s emptiness check would swallow it. This is what the MDC action
                // listener did, for the same reason.
                ErrorDetailsDialog(activity, errors).show()
                errors = mutableListOf()
            }
        }
    }

    /** `snackbar?.dismiss()`: cancelling the showing coroutine clears the host's current data. */
    fun dismiss() {
        job?.cancel()
        job = null
    }
}
