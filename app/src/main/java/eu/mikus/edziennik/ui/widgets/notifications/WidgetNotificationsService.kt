/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package eu.mikus.edziennik.ui.widgets.notifications

import android.content.Intent
import android.widget.RemoteViewsService
import eu.mikus.edziennik.App
import eu.mikus.edziennik.ui.widgets.WidgetConfig

class WidgetNotificationsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val app = application as App
        val config = intent.getStringExtra("config")?.let { app.gson.fromJson(it, WidgetConfig::class.java) }
        return WidgetNotificationsFactory(app, config ?: WidgetConfig(-1))
    }
}
