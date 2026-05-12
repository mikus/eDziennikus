/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-22.
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.entity.Teacher

data class RecipientListGetEvent(val profileId: Int, val teacherList: List<Teacher>)
