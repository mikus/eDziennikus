/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-1.
 */

package eu.mikus.edziennik.data.api

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ext.pendingIntentFlag
import eu.mikus.edziennik.receivers.SzkolnyReceiver
import kotlin.math.roundToInt


/**
 * Holds the foreground-service notification used during sync. Every mutator
 * here is `@Synchronized` because the same instance is touched from multiple
 * threads — the foreground-service main thread on idle/close, background
 * sync workers calling [setProgress] / [setCurrentTask] / [addError] /
 * [setCriticalError], and [post]'s `build()` reading the shared
 * `NotificationCompat.Builder` state. Without synchronization, the
 * `mActions.clear()` in [setCloseAction] / [setCancelAction] can race with
 * `build()` iterating that same list, producing a
 * `ConcurrentModificationException` deep inside NotificationCompat.
 */
class EdziennikNotification(val app: App) {
    private val notificationManager by lazy { app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val notificationBuilder: NotificationCompat.Builder by lazy {
        NotificationCompat.Builder(app, ApiService.NOTIFICATION_API_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(PRIORITY_MIN)
                .setOngoing(true)
                .setLocalOnly(true)
    }

    val notification: Notification
        @Synchronized
        get() = notificationBuilder.build()

    private var errorCount = 0
    private var criticalErrorCount = 0
    var serviceClosed = false

    private fun cancelPendingIntent(taskId: Int): PendingIntent {
        val intent = SzkolnyReceiver.getIntent(app, Bundle(
                "task" to "TaskCancelRequest",
                "taskId" to taskId
        ))
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag()) as PendingIntent
    }
    private val closePendingIntent: PendingIntent
        get() {
            val intent = SzkolnyReceiver.getIntent(app, Bundle(
                    "task" to "ServiceCloseRequest"
            ))
            return PendingIntent.getBroadcast(app, 0, intent, pendingIntentFlag()) as PendingIntent
        }

    private fun errorCountText(): String? {
        var result = ""
        if (criticalErrorCount > 0) {
            result += app.resources.getQuantityString(R.plurals.critical_errors_format, criticalErrorCount, criticalErrorCount)
        }
        if (criticalErrorCount > 0 && errorCount > 0) {
            result += ", "
        }
        if (errorCount > 0) {
            result += app.resources.getQuantityString(R.plurals.normal_errors_format, errorCount, errorCount)
        }
        return if (result.isEmpty()) null else result
    }

    @Synchronized
    fun setIdle(): EdziennikNotification {
        notificationBuilder.setContentTitle(app.getString(R.string.edziennik_notification_api_title))
        notificationBuilder.setProgress(0, 0, false)
        notificationBuilder.apply {
            val str = app.getString(R.string.edziennik_notification_api_text)
            setStyle(NotificationCompat.BigTextStyle().bigText(str))
            setContentText(str)
        }
        setCloseAction()
        return this
    }

    @Synchronized
    fun addError(): EdziennikNotification {
        errorCount++
        return this
    }

    @Synchronized
    fun setCriticalError(): EdziennikNotification {
        criticalErrorCount++
        notificationBuilder.setContentTitle(app.getString(R.string.edziennik_notification_api_error_title))
        notificationBuilder.setProgress(0, 0, false)
        notificationBuilder.apply {
            val str = errorCountText()
            setStyle(NotificationCompat.BigTextStyle().bigText(str))
            setContentText(str)
        }
        setCloseAction()
        return this
    }

    @Synchronized
    fun setProgress(progress: Float): EdziennikNotification {
        notificationBuilder.setProgress(100, progress.roundToInt(), progress < 0f)
        return this
    }

    @Synchronized
    fun setProgressText(progressText: String?): EdziennikNotification {
        notificationBuilder.setContentTitle(progressText)
        return this
    }

    @Synchronized
    fun setCurrentTask(taskId: Int, progressText: String?): EdziennikNotification {
        notificationBuilder.setProgress(100, 0, true)
        notificationBuilder.setContentTitle(progressText)
        notificationBuilder.apply {
            val str = errorCountText()
            setStyle(NotificationCompat.BigTextStyle().bigText(str))
            setContentText(str)
        }
        setCancelAction(taskId)
        return this
    }

    // NotificationCompat.Builder has no public API to clear actions; reach into the
    // @RestrictTo(LIBRARY_GROUP) mActions list to replace the set on each rebuild.
    // @Synchronized is required: mActions.clear() must not race with build()'s
    // iteration of that same list.
    @SuppressLint("RestrictedApi")
    @Synchronized
    fun setCloseAction(): EdziennikNotification {
        notificationBuilder.mActions.clear()
        notificationBuilder.addAction(
                NotificationCompat.Action(
                        R.drawable.ic_notification,
                        app.getString(R.string.edziennik_notification_api_close),
                        closePendingIntent
                ))
        return this
    }

    @SuppressLint("RestrictedApi")
    @Synchronized
    private fun setCancelAction(taskId: Int) {
        notificationBuilder.mActions.clear()
        notificationBuilder.addAction(
                NotificationCompat.Action(
                        R.drawable.ic_notification,
                        app.getString(R.string.edziennik_notification_api_cancel),
                        cancelPendingIntent(taskId)
                ))
    }

    @Synchronized
    fun post() {
        if (serviceClosed)
            return
        notificationManager.notify(app.notificationChannelsManager.sync.id, notification)
    }

}
