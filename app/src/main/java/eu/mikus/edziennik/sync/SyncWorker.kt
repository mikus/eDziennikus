package eu.mikus.edziennik.sync

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.*
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ApiService
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.ext.formatDate
import eu.mikus.edziennik.utils.Utils.d
import java.util.concurrent.TimeUnit
import java.util.UUID

class SyncWorker(val context: Context, val params: WorkerParameters) : Worker(context, params) {
    companion object {
        const val TAG = "SyncWorker"

        /**
         * Measured 2026-09-22 against a real profile: a full sync takes 8.6 s at full speed, 69.5 s
         * throttled to EDGE and 237.4 s throttled to GPRS. 300 s clears the measured worst case by
         * ~25% and sits inside the ~10-minute JobScheduler window. Note what it rests on: that
         * 237.4 s came from an emulator throttle, not a real congested network, so the true tail may
         * be longer.
         *
         * A `var` only so a test can shorten it: nothing services the bind under Robolectric, so
         * `doWork` would otherwise spend the full five minutes here before the reschedule it exists
         * to pin can be read.
         */
        @VisibleForTesting
        internal var SYNC_TIMEOUT_MS = 300_000L

        /**
         * Schedule the sync job only if it's not already scheduled.
         */
        @SuppressLint("RestrictedApi")
        fun scheduleNext(app: App, rescheduleIfFailedFound: Boolean = true) {
            WorkerUtils.scheduleNext(app, rescheduleIfFailedFound) {
                rescheduleNext(app)
            }
        }

        /**
         * Cancel any existing sync jobs and schedule a new one.
         *
         * If [ConfigSync.enabled] is not true, just cancel every job.
         */
        fun rescheduleNext(app: App, exceptId: UUID? = null) {
            cancelNext(app, exceptId)
            val enableSync = app.config.sync.enabled
            if (!enableSync) {
                return
            }
            val onlyWifi = app.config.sync.onlyWifi
            val syncInterval = app.config.sync.interval.toLong()

            val syncAt = System.currentTimeMillis() + syncInterval*1000
            d(TAG, "Scheduling work at ${syncAt.formatDate()}")

            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(
                            if (onlyWifi)
                                NetworkType.UNMETERED
                            else
                                NetworkType.CONNECTED)
                    .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInitialDelay(syncInterval, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build()

            WorkManager.getInstance(app).enqueue(syncWorkRequest)
        }

        /**
         * Cancel any scheduled sync job.
         *
         * [exceptId] spares one job, which is how a worker rescheduling from inside its own
         * `doWork` avoids cancelling itself — `cancelAllWorkByTag` has no "except me" form, and
         * `ExistingWorkPolicy.REPLACE` would cancel running work too. Callers outside a worker pass
         * null and get exactly today's behaviour; `App.onCreate` and the settings screen rely on
         * that.
         */
        fun cancelNext(app: App, exceptId: UUID? = null) {
            d(TAG, "Cancelling work by tag $TAG (sparing ${exceptId ?: "nothing"})")
            val workManager = WorkManager.getInstance(app)
            if (exceptId == null) {
                workManager.cancelAllWorkByTag(TAG)
                return
            }
            workManager.getWorkInfosByTag(TAG).get()
                    .filter { it.id != exceptId && !it.state.isFinished }
                    .forEach { workManager.cancelWorkById(it.id) }
            //WorkManager.getInstance(app).pruneWork() // do not prune the work in order to look for failed tasks
        }
    }

    override fun doWork(): Result {
        d(TAG, "Running worker ID ${params.id}")
        val app = context as App
        var ended = false
        try {
            // Bind and wait, rather than enqueue and return. IApiTask.enqueue would ask for a
            // foreground service, which the platform refuses from a cached process -- and returning
            // immediately would close the job's window while the sync is still in flight, because
            // runTask() dispatches and falls out and every Librus leg is an async enqueue. Nothing
            // in the tree joins; this is what joins.
            ended = ApiService.bindAndAwait(context, EdziennikTask.sync(), SYNC_TIMEOUT_MS)
            if (!ended)
                d(TAG, "The sync did not report an end within ${SYNC_TIMEOUT_MS}ms")
        } finally {
            // The hourly sync is a self-rescheduling chain of OneTimeWorkRequests, so anything that
            // escapes above takes every future sync with it. Measured 2026-09-22: a refused
            // foreground-service start threw here, this line never ran, and no job was left
            // scheduled at all -- WorkManager swallowed the throw into Result.FAILURE, so nothing
            // surfaced anywhere. params.id spares this worker's own job; see cancelNext.
            rescheduleNext(app, exceptId = params.id)
        }
        // retry, not success, when the sync never reported an end: the chain is already rescheduled
        // above, and Result.success() here would record a sync that did not happen.
        return if (ended) Result.success() else Result.retry()
    }
}
