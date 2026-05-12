/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-12.
 */

package eu.mikus.edziennik.ui.debug.models

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import eu.mikus.edziennik.ui.grades.models.ExpandableItemModel

data class LabJsonArray(
        val key: String,
        val jsonArray: JsonArray,
        override var level: Int
) : ExpandableItemModel<JsonElement>(jsonArray.toMutableList())
