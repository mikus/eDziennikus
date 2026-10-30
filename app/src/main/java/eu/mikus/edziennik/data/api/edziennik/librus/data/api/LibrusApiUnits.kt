/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-23.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_UNITS
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.ext.*

class LibrusApiUnits(override val data: DataLibrus,
                     override val lastSync: Long?,
                     val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiUnits"
    }

    init { run {
        if (data.unitIdUnknown) {
            // This branch makes no request, so it records no timer (see the contract on Data's
            // timer writer). When no unit id is known (DataLibrus.unitIdUnknown), the Classes
            // endpoint is where one comes from, and staying due lets a later run read it once
            // Classes has stored one. onSuccess is the dispatcher's continuation and must stay.
            onSuccess(ENDPOINT_LIBRUS_API_UNITS)
            return@run
        }

        apiGet(TAG, "Units") { json ->
            val units = json.getJsonArray("Units")?.asJsonObjectList()

            units?.singleOrNull { it.getLong("Id") == data.unitId }?.also { unit ->
                val startPoints = unit.getJsonObject("BehaviourGradesSettings")?.getJsonObject("StartPoints")
                startPoints?.apply {
                    data.startPointsSemester1 = getInt("Semester1", defaultValue = 0)
                    data.startPointsSemester2 = getInt("Semester2", defaultValue = data.startPointsSemester1)
                }
                unit.getJsonObject("GradesSettings")?.apply {
                    data.enablePointGrades = getBoolean("PointGradesEnabled", true)
                    data.enableDescriptiveGrades = getBoolean("DescriptiveGradesEnabled", true)
                }
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_UNITS, 7 * DAY)
            onSuccess(ENDPOINT_LIBRUS_API_UNITS)
        }
    }}
}
