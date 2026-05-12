/*
 * Copyright (c) Kacper Ziubryniewicz 2020-5-12
 */

package eu.mikus.edziennik.data.api.edziennik.podlasie

import eu.mikus.edziennik.data.api.models.Feature
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.data.db.enums.LoginType

const val ENDPOINT_PODLASIE_API_MAIN = 1001

val PodlasieFeatures = listOf(
        Feature(LoginType.PODLASIE, FeatureType.ALWAYS_NEEDED, listOf(
                ENDPOINT_PODLASIE_API_MAIN to LoginMethod.PODLASIE_API
        ))
)
