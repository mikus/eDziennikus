/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-7.
 */

package eu.mikus.edziennik.data.api.task

import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.utils.Utils.d

class SzkolnyTask(val app: App, val syncingProfiles: List<Profile>) : IApiTask(-1) {
    companion object {
        private const val TAG = "SzkolnyTask"
    }
    private val profiles by lazy { app.db.profileDao().allNow }
    override fun prepare(app: App) { taskName = app.getString(R.string.edziennik_szkolny_creating_notifications) }
    override fun cancel() {}

    private val notificationList = mutableListOf<Notification>()

    internal fun run(taskCallback: EdziennikCallback) {
        val startTime = System.currentTimeMillis()

        // create all e-register data notifications
        val notifications = Notifications(app, notificationList, profiles)
        notifications.run()

        // The AppSync cross-user shared-events sync against szkolny.eu's
        // backend was removed when SzkolnyApi was dropped from the fork.
        // What remains here is the local notification pipeline only.
        d(TAG, "Created ${notificationList.count()} notifications.")

        // filter notifications: each one is judged against ITS OWN profile's filter. Filtering by
        // type across the whole list let one profile's preference suppress another profile's
        // notifications, and setAllNotified below made that permanent rather than merely delayed.
        retainUnfilteredNotifications(notificationList) { app.config[it].sync.notificationFilter }

        // update the database
        app.db.metadataDao().setAllNotified(true)
        if (notificationList.isNotEmpty())
            app.db.notificationDao().addAll(notificationList)
        app.db.profileDao().setAllNotEmpty()

        // post all notifications
        PostNotifications(app, notificationList)
        d(TAG, "SzkolnyTask: finished in ${System.currentTimeMillis()-startTime} ms.")
        taskCallback.onCompleted()
    }
}
