/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-29.
 */

package eu.mikus.edziennik.ui.grades.models

import eu.mikus.edziennik.ui.grades.GradesAdapter.Companion.STATE_CLOSED

abstract class ExpandableItemModel<T>(open val items: MutableList<T>) {
    open var level: Int = 3
    var state: Int = STATE_CLOSED
}
