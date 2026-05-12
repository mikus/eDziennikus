/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-1.
 */

package eu.mikus.edziennik.data.api.task

import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.ApiService
import eu.mikus.edziennik.data.api.EdziennikNotification
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.utils.Utils

class ErrorReportTask : IApiTask(-1) {
    override fun prepare(app: App) {
        taskName = app.getString(R.string.edziennik_notification_api_error_report_title)
    }

    override fun cancel() {

    }

    fun run(app: App, taskCallback: EdziennikCallback, notification: EdziennikNotification, errorList: MutableList<ApiError>) {
        errorList.forEach { error ->
            Utils.d(ApiService.TAG, "Error ${error.tag} profile ${error.profileId}: code ${error.errorCode}")
        }
        errorList.clear()

        taskCallback.onCompleted()
    }


}
