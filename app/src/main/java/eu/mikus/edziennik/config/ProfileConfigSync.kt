/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-21.
 */

package eu.mikus.edziennik.config

import eu.mikus.edziennik.data.db.enums.NotificationType

@Suppress("RemoveExplicitTypeArguments")
class ProfileConfigSync(base: ProfileConfig) {

    var notificationFilter by base.config<Set<NotificationType>> {
        NotificationType.values()
            .filter { it.enabledByDefault == false }
            .toSet()
    }
}
