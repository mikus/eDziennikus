/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-9.
 */

package eu.mikus.edziennik.utils

import android.os.Bundle
import eu.mikus.edziennik.ui.base.enums.NavTarget

data class PausedNavigationData(
    val profileId: Int?,
    val navTarget: NavTarget?,
    val args: Bundle?,
)
