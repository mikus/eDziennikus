/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */
package eu.mikus.edziennik.ui.login

/** Route constants for the login Navigation-Compose graph (the 7 nav_login destinations). */
object LoginRoute {
    const val CHOOSER = "chooser"
    const val FORM = "form"
    const val PROGRESS = "progress"
    const val SUMMARY = "summary"
    const val SYNC = "sync"
    const val SYNC_ERROR = "syncError"
    const val FINISH = "finish"
}

/** What the login shell should do on Back (or the Chooser Cancel button) at a given [route]. */
sealed interface LoginBackAction {
    object Consume : LoginBackAction        // swallow — no-op (Progress/Sync/SyncError/Finish)
    object ConfirmCancel : LoginBackAction  // "are you sure" dialog, then cancel the activity (Summary)
    object CancelToHost : LoginBackAction   // cancel the activity immediately (Chooser, first entry)
    object Up : LoginBackAction             // pop the back stack (everything else)
}

/**
 * Pure back-navigation policy — a faithful port of LoginActivity.onBackPressedCallback's
 * per-destination switch. Drives both the system BackHandler and the Chooser's Cancel button.
 */
fun loginBackPolicy(route: String?, hasLoginStores: Boolean): LoginBackAction = when (route) {
    LoginRoute.PROGRESS, LoginRoute.SYNC, LoginRoute.SYNC_ERROR, LoginRoute.FINISH -> LoginBackAction.Consume
    LoginRoute.SUMMARY -> LoginBackAction.ConfirmCancel
    LoginRoute.CHOOSER -> if (!hasLoginStores) LoginBackAction.CancelToHost else LoginBackAction.Up
    else -> LoginBackAction.Up
}
