/*
 * Copyright (c) Mikolaj Olszewski 2026-9-13.
 */

package eu.mikus.edziennik.data.api.models

import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent

/**
 * A user action parked by `UserActionManager.sendToUser` for its notification to pick up later.
 *
 * This exists so the notification's intent carries no payload. `MainActivity` is the launcher
 * activity, so its `exported="true"` is permanent and any app can start it with arbitrary extras;
 * while the action's own fields travelled in those extras, a third party could pick the profile,
 * crash the activity through an out-of-range enum ordinal, and choose the origin and script of the
 * captcha WebView. Held here instead, none of it is reachable from an intent.
 *
 * It lives in the config store rather than a field because the notification outlives the process: a
 * cold start after process death must still find it. `BaseConfig.set` writes the in-memory map at
 * once but persists on `Dispatchers.IO`, so a kill inside that window can still lose it - one Room
 * insert wide, and the notification is posted immediately after. `ConfigDelegate` sends a top-level data class
 * through Gson, the same path as [Update], which is why this is flat values and not a `Bundle`.
 *
 * Deliberately a record with no behaviour: the meaning of these three values belongs to the captcha
 * flow, so the mapping to and from an event lives in `UserActionManager`, not here.
 */
data class PendingUserAction(
    val profileId: Int?,
    val type: UserActionRequiredEvent.Type,
    val siteKey: String,
    val referer: String,
    val userAgent: String,
)

/**
 * The parked action a notification tap should run, or `null`.
 *
 * Pure, so it is testable: the tap carries only a profile id, and one slot holds one action, so a
 * second profile parking an action before the first was tapped must make the first tap a **no-op**
 * rather than run the wrong profile's captcha. That is lossy - the first profile's action is dropped
 * and its next sync raises it again - and it is the deliberate trade for not introducing a
 * collection into the config store, which has no `Map`/`List` consumer today.
 */
fun pendingFor(pending: PendingUserAction?, profileId: Int?): PendingUserAction? =
    pending?.takeIf { it.profileId == profileId }
