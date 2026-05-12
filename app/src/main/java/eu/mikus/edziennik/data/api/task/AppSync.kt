/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-17.
 */

package eu.mikus.edziennik.data.api.task

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ERROR_API_INVALID_SIGNATURE
import eu.mikus.edziennik.data.api.szkolny.SzkolnyApi
import eu.mikus.edziennik.data.api.szkolny.SzkolnyApiException
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.ext.toErrorCode
import eu.mikus.edziennik.utils.models.Date

class AppSync(val app: App, val notifications: MutableList<Notification>, val profiles: List<Profile>, val api: SzkolnyApi) {
    companion object {
        private const val TAG = "AppSync"
    }

    /**
     * Run the app sync, sending all pending notifications
     * and retrieving a list of shared events.
     *
     * Events are automatically saved to app database,
     * along with corresponding metadata objects.
     *
     * @return a number of events inserted to DB, possibly needing a notification
     */
    fun run(lastSyncTime: Long, markAsSeen: Boolean = false): Int {
        val blacklistedIds = app.db.eventDao().blacklistedIds
        val (events, notes) = try {
            api.getEvents(profiles, notifications, blacklistedIds, lastSyncTime)
        } catch (e: SzkolnyApiException) {
            if (e.toErrorCode() == ERROR_API_INVALID_SIGNATURE)
                return 0
            throw e
        }

        app.config.sync.lastAppSync = System.currentTimeMillis()

        if (notes.isNotEmpty()) {
            app.db.noteDao().addAll(notes)
        }

        if (events.isNotEmpty()) {
            val today = Date.getToday()
            app.db.metadataDao().addAllIgnore(events.map { event ->
                val isPast = event.date < today
                Metadata(
                        event.profileId,
                        MetadataType.EVENT,
                        event.id,
                        isPast || markAsSeen || event.seen,
                        isPast || markAsSeen || event.notified
                )
            })
            return app.db.eventDao().upsertAll(events).size
        }
        return 0
    }
}
