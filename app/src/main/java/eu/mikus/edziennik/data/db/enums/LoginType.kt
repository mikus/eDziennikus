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
    PODLASIE(id = 6, features = FEATURES_PODLASIE),
    USOS(id = 7, features = FEATURES_USOS, schoolType = SchoolType.UNIVERSITY),
    DEMO(id = 8, features = setOf()),

    // the graveyard
    EDUDZIENNIK(id = 5, features = FEATURES_EDUDZIENNIK),
    IDZIENNIK(id = 3, features = FEATURES_IDZIENNIK),
}
