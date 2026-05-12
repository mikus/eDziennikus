/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-29.
 */

package eu.mikus.edziennik.data.api.interfaces

import com.google.gson.JsonObject
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.AnnouncementFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.MessageFull

interface EdziennikInterface {
    fun sync(featureTypes: Set<FeatureType>? = null, onlyEndpoints: Set<Int>? = null, arguments: JsonObject? = null)
    fun getMessage(message: MessageFull)
    fun sendMessage(recipients: Set<Teacher>, subject: String, text: String)
    fun markAllAnnouncementsAsRead()
    fun getAnnouncement(announcement: AnnouncementFull)
    fun getAttachment(owner: Any, attachmentId: Long, attachmentName: String)
    fun getRecipientList()
    fun getEvent(eventFull: EventFull)
    fun firstLogin()
    fun cancel()
}
