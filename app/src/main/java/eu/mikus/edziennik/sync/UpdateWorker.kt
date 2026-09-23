/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-18.
 */

package eu.mikus.edziennik.sync

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.models.Update
import eu.mikus.edziennik.ext.DAY
import eu.mikus.edziennik.ext.formatDate
import eu.mikus.edziennik.utils.Utils
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class UpdateWorker(val context: Context, val params: WorkerParameters) : Worker(context, params), CoroutineScope {
    companion object {
        const val TAG = "UpdateWorker"

        /**
         * Schedule the update job only if it's not already scheduled.
         *
         * `rescheduleIfFailedFound = false` deliberately, and not a parameter. Once the tag is its
         * own, the sync's calibration would land on a four-day job: a 1-minute overdue grace would
         * cancel and re-enqueue an update check four days out on every `MainActivity.onCreate`, and
         * a 15-minute App-Manager grace would raise the "your device is killing background work"
         * dialog after any overnight power-off. Neither can happen today only because the tag was
         * hardcoded to SyncWorker. An update check simply has no useful notion of "late".
         */
        fun scheduleNext(app: App) {
            WorkerUtils.scheduleNext(app, TAG, rescheduleIfFailedFound = false) {
                rescheduleNext(app)
            }
        }

        /**
         * Cancel any existing sync jobs and schedule a new one.
         *
         * If [ConfigSync.enabled] is not true, just cancel every job.
         */
        fun rescheduleNext(app: App) {
            cancelNext(app)
            if (!app.config.sync.notifyAboutUpdates) {
                return
            }
            val syncInterval = 4 * DAY;

            val syncAt = System.currentTimeMillis() + syncInterval*1000
            Utils.d(TAG, "Scheduling work at ${syncAt.formatDate()}")

            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setInitialDelay(syncInterval, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build()

            WorkManager.getInstance(app).enqueue(syncWorkRequest)
        }

        /**
         * Cancel any scheduled sync job.
         */
        fun cancelNext(app: App) {
            Utils.d(TAG, "Cancelling work by tag $TAG")
            WorkManager.getInstance(app).cancelAllWorkByTag(TAG)
        }
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun doWork(): Result {
        Utils.d(TAG, "Running worker ID ${params.id}")
        val app = context as App
        if (!app.config.sync.notifyAboutUpdates) {
            return Result.success()
        }

        val channel = if (App.devMode)
            Update.Type.BETA
        else
            Update.Type.RELEASE
        app.updateManager.checkNowSync(channel, notify = true)

        rescheduleNext(this.context)
        return Result.success()
    }
}
