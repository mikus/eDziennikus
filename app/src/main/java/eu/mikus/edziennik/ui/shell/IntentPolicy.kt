/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.ui.shell

/**
 * Which `handleIntent` actions an *untrusted* intent may trigger.
 *
 * `MainActivity` is the launcher activity, so its `exported="true"` is permanent: any installed app
 * can `startActivity` on it with arbitrary extras. An action whose only producer is an in-process
 * broadcast is therefore refused on that path - the broadcast receiver is registered in `onResume`
 * and is `RECEIVER_NOT_EXPORTED`, so the legitimate route is unaffected.
 *
 * Deliberately exhaustive with `else -> false`: a new action stays unreachable from an untrusted
 * intent until someone decides otherwise, rather than being externally reachable the moment it is
 * written.
 *
 * Its own file rather than `ShellPolicy.kt`, whose opening line is "Every decision the M3 app shell
 * makes" - folding intent admission in would falsify it. The package is right for a different
 * reason: `ui/shell` is where this project's pure, JVM-testable policy functions and their sibling
 * tests already live.
 */
fun actionAllowedFromLaunch(action: String?): Boolean = when (action) {
    // Reads nothing from the intent - it shows the app's own persisted update state.
    "updateRequest" -> true
    // Carries only a profile selector; the payload is parked internally.
    "userActionRequired" -> true
    // Producer is BetterLink's in-process broadcast, never a launch intent.
    "createManualEvent" -> false
    else -> false
}
