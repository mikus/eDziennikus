/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-12.
 */

package eu.mikus.edziennik.ui.debug.models

import com.google.gson.JsonElement

data class LabJsonElement(
        val key: String,
        val jsonElement: JsonElement,
        var level: Int
)
