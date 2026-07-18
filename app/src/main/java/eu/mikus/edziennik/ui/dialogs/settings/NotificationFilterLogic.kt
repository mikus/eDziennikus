/*
 * Copyright (c) Mikolaj Olszewski 2026-7-18.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import eu.mikus.edziennik.data.db.enums.NotificationType

/** The types the user unchecked = the [notificationFilter] set persisted to config. */
fun notificationFilterDisabled(
    eligible: List<NotificationType>,
    enabled: Set<NotificationType>,
): Set<NotificationType> = eligible.filter { it !in enabled }.toSet()

/** Whether to show the "are you sure" warning: user is disabling a normally-on notification. */
fun shouldWarnDisabling(disabled: Set<NotificationType>): Boolean =
    disabled.any { it.enabledByDefault == true }
