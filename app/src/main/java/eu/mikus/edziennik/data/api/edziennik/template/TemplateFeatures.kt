/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-11.
 */

package eu.mikus.edziennik.data.api.edziennik.template

import eu.mikus.edziennik.data.api.models.Feature
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.LoginMethod
import eu.mikus.edziennik.data.db.enums.LoginType

const val ENDPOINT_TEMPLATE_WEB_SAMPLE                  = 9991
const val ENDPOINT_TEMPLATE_WEB_SAMPLE_2                = 9992
const val ENDPOINT_TEMPLATE_API_SAMPLE                  = 9993

val TemplateFeatures = listOf(
        Feature(LoginType.TEMPLATE, FeatureType.STUDENT_INFO, listOf(
                ENDPOINT_TEMPLATE_WEB_SAMPLE to LoginMethod.TEMPLATE_WEB
        )),
        Feature(LoginType.TEMPLATE, FeatureType.SCHOOL_INFO, listOf(
                ENDPOINT_TEMPLATE_WEB_SAMPLE_2 to LoginMethod.TEMPLATE_WEB
        )),
        Feature(LoginType.TEMPLATE, FeatureType.GRADES, listOf(
                ENDPOINT_TEMPLATE_API_SAMPLE to LoginMethod.TEMPLATE_API
        ))
)
