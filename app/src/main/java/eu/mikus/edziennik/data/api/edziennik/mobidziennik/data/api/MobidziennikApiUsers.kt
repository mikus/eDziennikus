/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-6.
 */

package eu.mikus.edziennik.data.api.edziennik.mobidziennik.data.api

import eu.mikus.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.ext.fixName

class MobidziennikApiUsers(val data: DataMobidziennik, rows: List<String>) {
    init {
        for (row in rows) {
            if (row.isEmpty())
                continue
            val cols = row.split("|")

            if (cols[1] != "*")
                continue

            val id = cols[0].toLong()
            val name = cols[4].fixName()
            val surname = cols[5].fixName()

            data.teachersMap.put(id, "$surname $name")
            data.teacherList.put(id, Teacher(data.profileId, id, name, surname))
        }
    }
}
