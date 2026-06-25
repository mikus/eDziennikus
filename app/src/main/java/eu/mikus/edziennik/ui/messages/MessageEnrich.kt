/*
 * Copyright (c) Mikolaj Olszewski 2026-6-25.
 */

package eu.mikus.edziennik.ui.messages

import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.full.MessageFull

/**
 * Pure, Android-free recipient enrichment shared by the list + read ViewModels. The `@Relation` join does
 * not populate `MessageRecipientFull.fullName`, so it is filled here from the teachers snapshot. Mirrors the
 * recipient loop in `MessageManager.getMessage` (MessageManager.kt:86-104). [accountName] is non-null only
 * for the read view, which maps the self-recipient (`id == -1L`) to the account name; the list passes null.
 */
object MessageEnrich {
    fun enrichRecipients(message: MessageFull, teachers: List<Teacher>, accountName: String?) {
        val recipients = message.recipients ?: return
        recipients.retainAll { it.profileId == message.profileId }
        recipients.forEach { recipient ->
            if (accountName != null && recipient.id == -1L) {
                recipient.fullName = accountName
            }
            if (recipient.fullName == null) {
                recipient.fullName = teachers.firstOrNull { it.id == recipient.id }?.fullName ?: ""
            }
        }
    }
}
