/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-28.
 */

package eu.mikus.edziennik.data.api

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.*
import eu.mikus.edziennik.data.api.events.requests.ServiceCloseRequest
import eu.mikus.edziennik.data.api.events.requests.TaskCancelRequest
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.task.ErrorReportTask
import eu.mikus.edziennik.data.api.task.IApiTask
import eu.mikus.edziennik.data.api.task.SzkolnyTask
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.ext.toApiError
import eu.mikus.edziennik.utils.Utils.d
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

class ApiService : Service() {
    companion object {
        const val TAG = "ApiService"
        const val NOTIFICATION_API_CHANNEL_ID = "eu.mikus.edziennik.SYNC"
        fun start(context: Context) {
            context.startService(Intent(context, ApiService::class.java))
        }
        fun startAndRequest(context: Context, request: Any) {
            context.startService(Intent(context, ApiService::class.java))
            EventBus.getDefault().postSticky(request)
        }

        /**
         * Run [request] on this service and block until the sync reports it is over. Returns true if
         * it ended, false on timeout or a refused bind.
         *
         * **Binds rather than starts, and that is the whole point.** `BIND_AUTO_CREATE` creates the
         * service — running `onCreate`, which registers it on EventBus — without `onStartCommand`,
         * the sole caller of `startForeground`. A foreground start is refused from a cached process,
         * which is why the hourly background sync has been dead since targetSdk reached 31. `onBind`
         * returns null, so the connection reports `onNullBinding`; the binding still holds and still
         * keeps the service alive, which is all that is needed.
         *
         * Four details each have a wrong default:
         * - the subscriber is NON-sticky and the stale terminal sticky is cleared first. Both
         *   terminal events are postSticky'd and only MainActivity removes them, which does not
         *   exist during a background sync — a sticky subscriber would be handed the previous run's
         *   event at register() and return instantly, having synced nothing.
         * - only ApiTaskAllFinishedEvent releases it. ApiTaskErrorEvent ends a TASK, not the sync;
         *   releasing on it would unbind mid-sync and skip the trailing SzkolnyTask, the sole caller
         *   of setAllNotEmpty()/setAllNotified(true), silently poisoning the NEXT sync.
         * - on timeout the service is asked to stop, because the abandoned task is still writing and
         *   cancel() reaches Data.saveData(), a multi-DAO flush. Non-sticky: the service is still
         *   bound and therefore still registered, and a sticky that found no subscriber would abort
         *   the next sync instead.
         * - the finally releases the connection on EVERY exit, including a refused bind (Android
         *   requires it), and sweeps both the task and the cancel request off the bus.
         */
        /* Deliberately `internal` and NOT @VisibleForTesting: SyncWorker.doWork is a genuine
         * production caller in another class, so the annotation would be a lie and would raise a
         * VisibleForTests lint warning. `internal` already scopes this to the module, which is what
         * the tests need. */
        internal fun bindAndAwait(
            context: Context,
            request: IApiTask,
            timeoutMs: Long,
            graceMs: Long = CANCEL_GRACE_MS,
        ): Boolean {
            val bus = EventBus.getDefault()
            bus.removeStickyEvent(ApiTaskAllFinishedEvent::class.java)
            val done = CountDownLatch(1)
            val waiter = SyncEndWaiter(done)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) = Unit
                override fun onServiceDisconnected(name: ComponentName?) = Unit
                override fun onNullBinding(name: ComponentName?) = Unit
            }
            bus.register(waiter)
            try {
                val bound = context.bindService(
                    Intent(context, ApiService::class.java), connection, Context.BIND_AUTO_CREATE,
                )
                if (!bound) {
                    d(TAG, "Could not bind the sync service")
                    return false
                }
                bus.postSticky(request)
                if (done.await(timeoutMs, TimeUnit.MILLISECONDS))
                    return true
                d(TAG, "Sync timed out after ${timeoutMs}ms; asking the service to end it")
                bus.post(TaskCancelRequest(-1))
                return done.await(graceMs, TimeUnit.MILLISECONDS)
            } finally {
                runCatching { context.unbindService(connection) }
                runCatching { bus.unregister(waiter) }
                bus.removeStickyEvent(request)
                bus.removeStickyEvent(TaskCancelRequest::class.java)
            }
        }

        /** How long to let the service finish flushing after a timed-out sync is cancelled.
         *  A `var` only so tests need not pay it: nothing in production writes it. */
        @VisibleForTesting
        internal var CANCEL_GRACE_MS = 15_000L

        var lastEventTime = System.currentTimeMillis()
    }

    private val app by lazy { applicationContext as App }

    private val syncingProfiles = mutableListOf<Profile>()

    private var szkolnyTaskFinished = false
    private val allTaskRequestList = mutableListOf<Any>()
    private val taskQueue = mutableListOf<IApiTask>()
    private val errorList = mutableListOf<ApiError>()

    private var serviceClosed = false
        set(value) { field = value; notification.serviceClosed = value }
    private var taskCancelled = false
    private var taskIsRunning = false
    private var taskRunning: IApiTask? = null // for debug purposes
    private var taskRunningId = -1
    private var taskStartTime = 0L
    private var taskMaximumId = 0

    private var taskProfileId = -1
    private var taskProgress = -1f
    private var taskProgressText: String? = null

    private val notification by lazy { EdziennikNotification(app) }

    /*    ______    _     _                  _ _       _____      _ _ _                _
         |  ____|  | |   (_)                (_) |     / ____|    | | | |              | |
         | |__   __| |_____  ___ _ __  _ __  _| | __ | |     __ _| | | |__   __ _  ___| | __
         |  __| / _` |_  / |/ _ \ '_ \| '_ \| | |/ / | |    / _` | | | '_ \ / _` |/ __| |/ /
         | |___| (_| |/ /| |  __/ | | | | | | |   <  | |___| (_| | | | |_) | (_| | (__|   <
         |______\__,_/___|_|\___|_| |_|_| |_|_|_|\_\  \_____\__,_|_|_|_.__/ \__,_|\___|_|\*/
    private val taskCallback = object : EdziennikCallback {
        override fun onCompleted() {
            lastEventTime = System.currentTimeMillis()
            d(TAG, "Task $taskRunningId (profile $taskProfileId) finished in ${System.currentTimeMillis()-taskStartTime}")
            EventBus.getDefault().postSticky(ApiTaskFinishedEvent(taskProfileId))
            clearTask()

            notification.setIdle().post()
            runTask()
        }

        override fun onRequiresUserAction(event: UserActionRequiredEvent) {
            app.userActionManager.sendToUser(event)
            taskRunning?.cancel()
            clearTask()
            runTask()
        }

        override fun onError(apiError: ApiError) {
            lastEventTime = System.currentTimeMillis()
            d(TAG, "Task $taskRunningId threw an error - $apiError")
            apiError.profileId = taskProfileId

            markSeenIfMessageGone(apiError)

            EventBus.getDefault().postSticky(ApiTaskErrorEvent(apiError))
            errorList.add(apiError)
            apiError.throwable?.printStackTrace()

            if (apiError.isCritical) {
                taskRunning?.cancel()
                notification.setCriticalError().post()
                clearTask()
                runTask()
            }
            else {
                notification.addError().post()
            }
        }

        override fun onProgress(step: Float) {
            lastEventTime = System.currentTimeMillis()
            if (step <= 0)
                return
            if (taskProgress < 0)
                taskProgress = 0f
            taskProgress += step
            taskProgress = min(100f, taskProgress)
            d(TAG, "Task $taskRunningId progress: ${taskProgress.roundToInt()}%")
            EventBus.getDefault().post(ApiTaskProgressEvent(taskProfileId, taskProgress, taskProgressText))
            notification.setProgress(taskProgress).post()
        }

        override fun onStartProgress(stringRes: Int) {
            lastEventTime = System.currentTimeMillis()
            taskProgressText = getString(stringRes)
            d(TAG, "Task $taskRunningId progress: $taskProgressText")
            EventBus.getDefault().post(ApiTaskProgressEvent(taskProfileId, taskProgress, taskProgressText))
            notification.setProgressText(taskProgressText).post()
        }
    }

    /**
     * Clears the unread badge of a message the server no longer has.
     *
     * A body fetch that fails with [ERROR_LIBRUS_MESSAGES_NOT_FOUND] can never succeed - the message
     * was deleted server-side - so its body stays null forever and the body-gated seen-write in
     * MessageReadViewModel/MessageManager never fires, leaving an unread badge the user cannot clear.
     *
     * Only that one code qualifies. Transient failures (timeouts, request failures, expired sessions)
     * must leave the message unread so a later retry can still deliver the body.
     *
     * This runs upstream of the [ApiTaskErrorEvent] post on purpose: the failing task itself still
     * knows which message was requested, so the fix needs no error subscription in the (deliberately
     * EventBus-free) Compose message screen, and no UI has to be alive for it to take effect.
     */
    private fun markSeenIfMessageGone(apiError: ApiError) {
        if (apiError.errorCode != ERROR_LIBRUS_MESSAGES_NOT_FOUND)
            return
        val request = (taskRunning as? EdziennikTask)?.request as? EdziennikTask.MessageGetRequest ?: return
        val message = request.message
        if (message.seen)
            return
        d(TAG, "Message ${message.id} is gone from the server - marking it as read")
        app.db.metadataDao().setSeen(message.profileId, message, true)
    }

    /*    _______        _                               _   _
         |__   __|      | |                             | | (_)
            | | __ _ ___| | __   _____  _____  ___ _   _| |_ _  ___  _ __
            | |/ _` / __| |/ /  / _ \ \/ / _ \/ __| | | | __| |/ _ \| '_ \
            | | (_| \__ \   <  |  __/>  <  __/ (__| |_| | |_| | (_) | | | |
            |_|\__,_|___/_|\_\  \___/_/\_\___|\___|\__,_|\__|_|\___/|_| |*/
    private fun runTask() {
        checkIfTaskFrozen()
        if (taskIsRunning)
            return
        if (taskCancelled || serviceClosed || (taskQueue.isEmpty() && szkolnyTaskFinished)) {
            allCompleted()
            return
        }

        lastEventTime = System.currentTimeMillis()

        val task = if (taskQueue.isNotEmpty()) {
            taskQueue.removeAt(0)
        } else {
            szkolnyTaskFinished = true
            SzkolnyTask(app, syncingProfiles)
        }

        task.taskId = ++taskMaximumId
        task.prepare(app)
        taskIsRunning = true
        taskRunningId = task.taskId
        taskRunning = task
        taskProfileId = task.profileId
        taskProgress = -1f
        taskProgressText = task.taskName

        d(TAG, "Executing task $taskRunningId - ${task::class.java.name}")

        // update the notification
        notification.setCurrentTask(taskRunningId, taskProgressText).post()

        // post an event
        EventBus.getDefault().post(ApiTaskStartedEvent(taskProfileId, task.profile))

        task.profile?.let { syncingProfiles.add(it) }

        taskStartTime = System.currentTimeMillis()
        try {
            when (task) {
                is EdziennikTask -> task.run(app, taskCallback)
                is ErrorReportTask -> task.run(app, taskCallback, notification, errorList)
                is SzkolnyTask -> task.run(taskCallback)
            }
        } catch (e: Exception) {
            taskCallback.onError(e.toApiError(TAG))
        }
    }

    /**
     * Check if a task is inactive for more than 30 seconds.
     *
     * This usually means it is broken and won't become active again.
     * This drops the service's pointers to the task; it does NOT call IApiTask.cancel(), so the
     * task itself keeps running until its own HTTP timeout.
     */
    private fun checkIfTaskFrozen() {
        if (System.currentTimeMillis() - lastEventTime > 30*1000) {
            val time = System.currentTimeMillis() - lastEventTime
            d(TAG, "!!! Task $taskRunningId froze for $time ms. $taskRunning")
            clearTask()
        }
    }

    /**
     * Remove any task descriptors or pointers from the service.
     */
    private fun clearTask() {
        taskIsRunning = false
        taskRunningId = -1
        taskRunning = null
        taskProfileId = -1
        taskProgress = -1f
        taskProgressText = null
        taskCancelled = false
    }

    private fun allCompleted() {
        serviceClosed = true
        EventBus.getDefault().postSticky(ApiTaskAllFinishedEvent())
        stopSelf()
    }

    /*    ______               _   ____
         |  ____|             | | |  _ \
         | |____   _____ _ __ | |_| |_) |_   _ ___
         |  __\ \ / / _ \ '_ \| __|  _ <| | | / __|
         | |___\ V /  __/ | | | |_| |_) | |_| \__ \
         |______\_/ \___|_| |_|\__|____/ \__,_|__*/
    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    fun onApiTask(task: IApiTask) {
        EventBus.getDefault().removeStickyEvent(task)
        d(TAG, task.toString())

        if (task is EdziennikTask) {
            // fix for duplicated tasks, thank you EventBus
            if (task.request in allTaskRequestList)
                return
            allTaskRequestList += task.request
        }

        if (task is EdziennikTask) {
            when (task.request) {
                is EdziennikTask.SyncRequest -> app.db.profileDao().idsForSyncNow.forEach {
                    taskQueue += EdziennikTask.syncProfile(it)
                }
                is EdziennikTask.SyncProfileListRequest -> task.request.profileList.forEach {
                    taskQueue += EdziennikTask.syncProfile(it)
                }
                else -> {
                    taskQueue += task
                }
            }
        }
        else {
            taskQueue += task
        }
        d(TAG, "EventBus received an IApiTask: $task")
        d(TAG, "Current queue:")
        taskQueue.forEach {
            d(TAG, "  - $it")
        }
        runTask()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    fun onTaskCancelRequest(request: TaskCancelRequest) {
        EventBus.getDefault().removeStickyEvent(request)
        d(TAG, request.toString())

        // A tap on Anuluj is an instruction, not a question: it ends the whole sync, which is what
        // runTask()'s taskCancelled arm has always intended - that arm simply never fired, because
        // clearTask() resets the flag before any callback path reaches it.
        //
        // Aborting means fetching no further profiles, NOT skipping the wrap-up. Clearing the queue
        // and letting runTask() fall through to its tail runs SzkolnyTask, the only caller of
        // setAllNotEmpty() - and ~14 endpoints stamp Metadata's seen/notified from profile.empty, so
        // skipping it would leave that flag set and make the NEXT sync write every row as
        // already-seen: no notifications, no unread badges. runTask() always reaches allCompleted()
        // from here, because the queue is now empty, so the terminal event is still guaranteed.
        //
        // try/finally because cancel() reaches Data.saveData(), a multi-DAO flush: it must finish
        // before the sync is declared over, yet a throw in it must not cost the terminal event -
        // this ASYNC subscriber would swallow the throw and onDestroy's guard would then suppress
        // the retry. clearTask() stays after cancel(), because it nulls taskRunning.
        taskQueue.clear()
        try {
            taskRunning?.cancel()
        } finally {
            clearTask()
            runTask()
        }
    }
    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    fun onServiceCloseRequest(request: ServiceCloseRequest) {
        EventBus.getDefault().removeStickyEvent(request)
        d(TAG, request.toString())

        serviceClosed = true
        taskCancelled = true
        // try/finally, so both properties hold. cancel() reaches Data.saveData(), a multi-DAO flush,
        // and it must finish before allCompleted() calls stopSelf() and drops the foreground-service
        // importance - otherwise the sync's own data can still be in flight when the process becomes
        // evictable. But a throw in that flush must not skip the terminal post: it would be swallowed
        // here (throwSubscriberException defaults to false) and onDestroy's guard would suppress the
        // second chance too, because serviceClosed is already true.
        try {
            taskRunning?.cancel()
        } finally {
            allCompleted()
        }
    }

    /*     _____                 _                                     _     _
          / ____|               (_)                                   (_)   | |
         | (___   ___ _ ____   ___  ___ ___    _____   _____ _ __ _ __ _  __| | ___  ___
          \___ \ / _ \ '__\ \ / / |/ __/ _ \  / _ \ \ / / _ \ '__| '__| |/ _` |/ _ \/ __|
          ____) |  __/ |   \ V /| | (_|  __/ | (_) \ V /  __/ |  | |  | | (_| |  __/\__ \
         |_____/ \___|_|    \_/ |_|\___\___|  \___/ \_/ \___|_|  |_|  |_|\__,_|\___||__*/
    override fun onCreate() {
        d(TAG, "Service created")
        EventBus.getDefault().register(this)
        notification.setIdle().setCloseAction()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        d(TAG, "Foreground service onStartCommand")
        notification.serviceStarted = true
        startForeground(app.notificationChannelsManager.sync.id, notification.notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        d(TAG, "Service destroyed")
        // The guard reads serviceClosed BEFORE this method sets it. allCompleted() assigns the flag
        // before it posts, so a false flag here means no terminal event was ever sent - the platform
        // took the service (FGS reclaim, the Android 15 dataSync budget) and no
        // consumer will ever be told. Two placements are wrong and both compile:
        //   - below the assignment: the condition is always false and this does nothing;
        //   - inside allCompleted(): runTask()'s serviceClosed arm posts for a task enqueued into a
        //     closing service, and that post is the only thing clearing the spinner SyncTrigger
        //     turned on eagerly via markRefreshing(). Guarding there trades a silent no-op for a
        //     permanently stuck spinner.
        if (!serviceClosed)
            EventBus.getDefault().postSticky(ApiTaskAllFinishedEvent())
        serviceClosed = true
        EventBus.getDefault().unregister(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

/** Releases [done] when the sync reports it is over. Registered non-sticky by
 *  [ApiService.bindAndAwait], and deliberately NOT subscribed to ApiTaskErrorEvent. */
class SyncEndWaiter(private val done: CountDownLatch) {
    @Subscribe
    fun onAllFinished(event: ApiTaskAllFinishedEvent) = done.countDown()
}
