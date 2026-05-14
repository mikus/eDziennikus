/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-17.
 */

package eu.mikus.edziennik.data.db.enums

enum class LoginType(
    val id: Int,
    val features: Set<FeatureType>,
    val schoolType: SchoolType = SchoolType.STANDARD,
) {
    LIBRUS(id = 2, features = FEATURES_LIBRUS),
    DEMO(id = 8, features = setOf()),
}
