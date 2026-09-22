/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-28.
 */

package eu.mikus.edziennik.data.api.task

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ApiService
import eu.mikus.edziennik.data.api.ERROR_SERVICE_START_REFUSED
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.Profile

abstract class IApiTask(open val profileId: Int) {
    var taskId: Int = 0
    var profile: Profile? = null
    var taskName: String? = null

    /**
     * A method called before running the task.
     * It is synchronous and its main task is
     * to prepare the correct task name.
     */
    abstract fun prepare(app: App)
    abstract fun cancel()

    fun enqueue(context: Context) {
        Intent(context, ApiService::class.java).let {
            try {
                if (SDK_INT >= O)
                    context.startForegroundService(it)
                else
                    context.startService(it)
            } catch (e: IllegalStateException) {
                // Android 12+ refuses a foreground-service start from a background process. The
                // exception is ForegroundServiceStartNotAllowedException, added in API 31; it is
                // caught by its IllegalStateException parent so this still compiles against
                // minSdk 23 without a version gate.
                //
                // Reported, not rethrown: an escaping throw kills the process, and
                // CustomActivityOnCrash cannot even show its dialog from the background, so the
                // user gets nothing at all. Reported, not swallowed, for the opposite reason --
                // syncFeature calls markRefreshing() five lines before reaching here and
                // SyncStatus clears isRefreshing only on AllFinished or Error, so a silent catch
                // would leave a spinner that nothing can ever stop. Sticky, so a failure raised
                // from a backgrounded process still reaches the UI when it next opens.
                EventBus.getDefault().postSticky(
                    ApiTaskErrorEvent(ApiError(TAG, ERROR_SERVICE_START_REFUSED).apply {
                        throwable = e
                    })
                )
                // Before postSticky(this), deliberately: onApiTask is a sticky subscriber, so a
                // task left on the bus would fire against whichever service starts next.
                return
            }
        }
        EventBus.getDefault().postSticky(this)
    }

    override fun toString(): String {
        return "IApiTask(profileId=$profileId, taskId=$taskId, profile=$profile, taskName=$taskName)"
    }

    companion object {
        private const val TAG = "IApiTask"

        fun enqueueAll(context: Context, tasks: List<IApiTask>) {
            if (tasks.isEmpty())
                return
            Intent(context, ApiService::class.java).let {
                if (SDK_INT >= O)
                    context.startForegroundService(it)
                else
                    context.startService(it)
            }
            tasks.forEach {
                EventBus.getDefault().postSticky(it)
            }
        }
    }
}
