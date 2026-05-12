/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-26.
 */

package eu.mikus.edziennik.config

import eu.mikus.edziennik.utils.models.Time

@Suppress("RemoveExplicitTypeArguments")
class ConfigTimetable(base: Config) {

    var bellSyncMultiplier by base.config<Int>(0)
    var bellSyncDiff by base.config<Time?>(null)
    var countInSeconds by base.config<Boolean>(false)
}
