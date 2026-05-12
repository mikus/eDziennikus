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
            if (SDK_INT >= O)
                context.startForegroundService(it)
            else
                context.startService(it)
        }
        EventBus.getDefault().postSticky(this)
    }

    override fun toString(): String {
        return "IApiTask(profileId=$profileId, taskId=$taskId, profile=$profile, taskName=$taskName)"
    }

    companion object {
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
