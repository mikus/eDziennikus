/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-17.
 */

package eu.mikus.edziennik.ui.base.enums

enum class NavTargetLocation {
    NOWHERE,
    DRAWER,
    DRAWER_MORE,
    DRAWER_BOTTOM,
    PROFILE_LIST,
    /** Memberless since Phase 40 deleted `DEBUG`. Kept as a seam - see `AppSheet`'s `baseRows`. */
    BOTTOM_SHEET,
}
