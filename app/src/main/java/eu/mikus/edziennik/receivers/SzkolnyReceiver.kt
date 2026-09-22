/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-1.
 */

package eu.mikus.edziennik.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import eu.mikus.edziennik.data.api.ApiService
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.requests.ServiceCloseRequest
import eu.mikus.edziennik.data.api.events.requests.TaskCancelRequest

class SzkolnyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "eu.mikus.edziennik.SZKOLNY_MAIN"

        /**
         * Distinct `PendingIntent` request codes, one per action that targets this receiver.
         *
         * They must differ from each other. `PendingIntent` matching is
         * (package, factory kind, requestCode, [android.content.Intent.filterEquals]) and **extras
         * are excluded** (mutability is not), so intents that differ only in their `task` extra
         * collapse into one record - and a site passing `FLAG_UPDATE_CURRENT` (here: cancel)
         * rewrites the extras that every other site then delivers, whenever it is created.
         *
         * Cancel keeps 0 so tokens already minted on upgraded installs keep resolving to it.
         */
        const val REQUEST_TASK_CANCEL = 0
        const val REQUEST_SERVICE_CLOSE = 1
        const val REQUEST_WIDGET_SYNC = 2

        fun getIntent(context: Context, extras: Bundle): Intent {
            val intent = Intent(context, SzkolnyReceiver::class.java)
            intent.putExtras(extras)
            return intent
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        when (intent?.extras?.getString("task", null)) {
            "ServiceCloseRequest" -> ApiService.startAndRequest(context, ServiceCloseRequest())
            "TaskCancelRequest" -> ApiService.startAndRequest(context, TaskCancelRequest(intent.extras?.getInt("taskId", -1) ?: return))
            "SyncRequest" -> EdziennikTask.sync().enqueue(context)
            "SyncProfileRequest" -> EdziennikTask.syncProfile(intent.extras?.getInt("profileId", -1) ?: return).enqueue(context)
        }
    }
}
