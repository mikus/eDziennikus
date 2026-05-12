/*
 * Copyright (c) Kacper Ziubryniewicz 2019-10-14
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_LUCKY_NUMBER
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusApi
import eu.mikus.edziennik.data.db.entity.LuckyNumber
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

class LibrusApiLuckyNumber(override val data: DataLibrus,
                           override val lastSync: Long?,
                           val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiLuckyNumber"
    }

    init {
        var nextSync = System.currentTimeMillis() + 2* DAY *1000

        apiGet(TAG, "LuckyNumbers") { json ->
            if (json.isJsonNull) {
                //profile?.luckyNumberEnabled = false
            } else {
                json.getJsonObject("LuckyNumber")?.also { luckyNumberEl ->

                    val luckyNumberDate = Date.fromY_m_d(luckyNumberEl.getString("LuckyNumberDay")) ?: Date.getToday()
                    val luckyNumber = luckyNumberEl.getInt("LuckyNumber") ?: -1
                    val luckyNumberObject = LuckyNumber(
                            profileId = profileId,
                            date = luckyNumberDate,
                            number = luckyNumber
                    )

                    if (luckyNumberDate >= Date.getToday())
                        nextSync = luckyNumberDate.combineWith(Time(15, 0, 0))
                    else
                        nextSync = System.currentTimeMillis() + 6* HOUR *1000

                    data.luckyNumberList.add(luckyNumberObject)
                    data.metadataList.add(
                            Metadata(
                                    profileId,
                                    MetadataType.LUCKY_NUMBER,
                                    luckyNumberObject.date.value.toLong(),
                                    true,
                                    profile?.empty ?: false
                            ))
                }
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_LUCKY_NUMBER, syncAt = nextSync)
            onSuccess(ENDPOINT_LIBRUS_API_LUCKY_NUMBER)
        }
    }
}
