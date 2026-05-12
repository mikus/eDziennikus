/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-22.
 */

package eu.mikus.edziennik.utils.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.App
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.szkolny.response.Update
import eu.mikus.edziennik.data.api.task.PostNotifications
import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.data.db.enums.NotificationType
import eu.mikus.edziennik.ext.concat
import eu.mikus.edziennik.ext.resolveString
import eu.mikus.edziennik.utils.html.BetterHtml
import kotlin.coroutines.CoroutineContext

class UpdateManager(val app: App) : CoroutineScope {
    companion object {
        private const val TAG = "UpdateManager"
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

    /**
     * Check for updates on the specified [maxChannel].
     * If the running build is of "more-unstable" type,
     * that channel is used instead.
     *
     * Optionally, post a notification if [notify] is true.
     *
     * @return [Result] containing a newer update, or null if not available
     */
    suspend fun checkNow(
        maxChannel: Update.Type,
        notify: Boolean,
    ): Result<Update?> = withContext(Dispatchers.IO) {
        return@withContext checkNowSync(maxChannel, notify)
    }

    /**
     * Check for updates on the specified [maxChannel].
     * If the running build is of "more-unstable" type,
     * that channel is used instead.
     *
     * Optionally, post a notification if [notify] is true.
     *
     * @return [Result] containing a newer update, or null if not available
     */
    fun checkNowSync(
        maxChannel: Update.Type,
        notify: Boolean,
    ): Result<Update?> {
        val channel = minOf(app.buildManager.releaseType, maxChannel)
        val update = app.api.runCatching({
            getUpdate(channel).firstOrNull()
        }, {
            return Result.failure(it)
        })
        return Result.success(process(update, notify))
    }

    /**
     * Process the update: check if the version is newer, and optionally
     * post a notification.
     *
     * @return [update] if it's a newer version, null otherwise
     */
    fun process(update: Update?, notify: Boolean): Update? {
        if (update == null || update.versionCode <= BuildConfig.VERSION_CODE) {
            app.config.update = null
            return null
        }
        app.config.update = update

        if (EventBus.getDefault().hasSubscriberForEvent(update::class.java)) {
            EventBus.getDefault().postSticky(update)
            return update
        }

        if (notify)
            notify(update)
        return update
    }

    fun notify(update: Update) {
        if (!app.config.sync.notifyAboutUpdates)
            return
        val bigText = listOf(
            app.getString(R.string.notification_updates_text, update.versionName),
            update.releaseNotes?.let { BetterHtml.fromHtml(context = null, it) },
        )
        val notification = Notification(
            id = System.currentTimeMillis(),
            title = R.string.notification_updates_title.resolveString(app),
            text = bigText.concat("\n").toString(),
            type = NotificationType.UPDATE,
            profileId = null,
            profileName = R.string.notification_updates_title.resolveString(app),
        ).addExtra("action", "updateRequest")
        app.db.notificationDao().add(notification)
        PostNotifications(app, listOf(notification))
    }
}
