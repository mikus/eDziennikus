/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-25.
 */
package eu.mikus.edziennik.data.db.full

import androidx.room.Relation
import eu.mikus.edziennik.data.db.entity.Announcement
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.utils.models.Date

class AnnouncementFull(
        profileId: Int, id: Long,
        subject: String, text: String?,
        startDate: Date?, endDate: Date?,
        teacherId: Long, addedDate: Long = System.currentTimeMillis()
) : Announcement(
        profileId, id,
        subject, text,
        startDate, endDate,
        teacherId, addedDate
), Noteable {
    var teacherName: String? = null

    // metadata
    var seen = false
    var notified = false

    @Relation(parentColumn = "announcementId", entityColumn = "noteOwnerId", entity = Note::class)
    override lateinit var notes: MutableList<Note>
    override fun getNoteType() = Note.OwnerType.ANNOUNCEMENT
    override fun getNoteOwnerProfileId() = profileId
    override fun getNoteOwnerId() = id
}
