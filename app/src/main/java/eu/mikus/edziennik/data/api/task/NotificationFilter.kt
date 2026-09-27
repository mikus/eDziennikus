/*
 * Copyright (c) Mikolaj Olszewski 2026-9-27.
 */

package eu.mikus.edziennik.data.api.task

import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.data.db.enums.NotificationType

/**
 * Drop from [notifications] every entry whose OWN profile has filtered its type, keeping the rest in
 * their original relative order.
 *
 * [filterFor] is a profile's persisted `sync.notificationFilter`, i.e. the types it suppresses. An
 * entry with no profile is kept — no profile's preference applies to it.
 *
 * The list spans every profile, so an entry must be judged against its OWN profile's filter rather
 * than against types alone. The call site carries what went wrong when it was not.
 *
 * Mutates in place rather than returning a new list so the traversal stays inside the tested unit and
 * the caller has no result it could silently drop. Mirrors MessageEnrich.enrichRecipients.
 */
internal fun retainUnfilteredNotifications(
    notifications: MutableList<Notification>,
    filterFor: (profileId: Int) -> Set<NotificationType>,
) {
    notifications.retainAll { it.profileId == null || it.type !in filterFor(it.profileId) }
}
