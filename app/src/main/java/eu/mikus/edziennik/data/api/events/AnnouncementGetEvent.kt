/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-26
 */

package eu.mikus.edziennik.data.api.events

import eu.mikus.edziennik.data.db.full.AnnouncementFull

data class AnnouncementGetEvent(val announcement: AnnouncementFull)
