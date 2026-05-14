/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-26.
 */

package eu.mikus.edziennik.config

import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.data.api.szkolny.response.RegisterAvailabilityStatus
import eu.mikus.edziennik.ext.HOUR
import eu.mikus.edziennik.utils.models.Time

@Suppress("RemoveExplicitTypeArguments")
class ConfigSync(base: Config) {

    var enabled by base.config<Boolean>("syncEnabled", true)
    var interval by base.config<Int>("syncInterval", 1 * HOUR.toInt())
    var onlyWifi by base.config<Boolean>("syncOnlyWifi", false)

    var dontShowAppManagerDialog by base.config<Boolean>(false)
    var lastAppSync by base.config<Long>(0L)
    var notifyAboutUpdates by base.config<Boolean>(true)

    // Quiet Hours
    var quietHoursEnabled by base.config<Boolean>(false)
    var quietHoursStart by base.config<Time?>(null)
    var quietHoursEnd by base.config<Time?>(null)
    var quietDuringLessons by base.config<Boolean>(false)

    // Register Availability
    private var registerAvailabilityMap by base.config<Map<String, RegisterAvailabilityStatus>>("registerAvailability") { mapOf() }
    private var registerAvailabilityFlavor by base.config<String?>(null)

    var registerAvailability: Map<String, RegisterAvailabilityStatus>
        get() {
            if (BuildConfig.FLAVOR != registerAvailabilityFlavor)
                return mapOf()
            return registerAvailabilityMap
        }
        set(value) {
            registerAvailabilityMap = value
            registerAvailabilityFlavor = BuildConfig.FLAVOR
        }
}
