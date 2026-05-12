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
