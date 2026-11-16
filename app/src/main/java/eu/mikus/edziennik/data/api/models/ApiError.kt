/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-28.
 */

package eu.mikus.edziennik.data.api.models

import android.content.Context
import android.os.Bundle
import com.google.gson.JsonObject
import im.wangchao.mhttp.Request
import im.wangchao.mhttp.Response
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.ERROR_API_EXCEPTION
import eu.mikus.edziennik.data.api.ERROR_EXCEPTION
import eu.mikus.edziennik.ext.stackTraceString
import eu.mikus.edziennik.ext.toErrorCode

class ApiError(val tag: String, var errorCode: Int) {
    companion object {
        fun fromThrowable(tag: String, throwable: Throwable) =
                ApiError(tag, throwable.toErrorCode() ?: ERROR_EXCEPTION)
                        .withThrowable(throwable)
    }

    val id = System.currentTimeMillis()
    var profileId: Int? = null
    var throwable: Throwable? = null
    var apiResponse: String? = null
    var request: Request? = null
    var response: Response? = null
    var isCritical = true
    var params: Bundle? = null

    fun withThrowable(throwable: Throwable?): ApiError {
        this.throwable = throwable
        return this
    }
    fun withApiResponse(apiResponse: String?): ApiError {
        this.apiResponse = apiResponse
        return this
    }
    fun withApiResponse(apiResponse: JsonObject?): ApiError {
        this.apiResponse = apiResponse?.toString()
        return this
    }
    fun withRequest(request: Request?): ApiError {
        this.request = request
        return this
    }
    fun withResponse(response: Response?): ApiError {
        this.response = response
        this.request = response?.request()
        return this
    }

    /**
     * Whether this error may be the failure of a task enqueued for [profileId].
     *
     * [ApiService][eu.mikus.edziennik.data.api.ApiService]'s `onError` stamps every error of a running
     * task with that task's profile. A stamp of -1 (SzkolnyTask or ErrorReportTask failing, or an
     * error arriving after its task was cleared) never matches a real profile. An error with no stamp
     * was not attributed to any task, so it may be anyone's: today the only one is
     * [IApiTask.enqueue][eu.mikus.edziennik.data.api.task.IApiTask.enqueue]'s
     * `ERROR_SERVICE_START_REFUSED`, posted inline when the service start is refused. A caller that
     * waits on a task it enqueued that way must count it, or nothing ends its wait.
     */
    fun mayBelongTo(profileId: Int): Boolean =
        this.profileId == null || this.profileId == profileId

    fun setCritical(isCritical: Boolean): ApiError {
        this.isCritical = isCritical
        return this
    }

    fun withParams(bundle: Bundle): ApiError {
        this.params = bundle
        return this
    }

    fun getStringText(context: Context): String {
        return context.resources.getIdentifier("error_${errorCode}", "string", context.packageName).let {
            if (it != 0)
                context.getString(it)
            else
                "?"
        }
    }

    fun getStringReason(context: Context): String {
        return context.resources.getIdentifier("error_${errorCode}_reason", "string", context.packageName).let {
            if (it != 0)
                context.getString(it)
            else
                context.getString(R.string.error_unknown_format, errorCode, tag)
        }
    }

    override fun toString(): String {
        return "ApiError(tag='$tag', errorCode=$errorCode, profileId=$profileId, throwable=$throwable, apiResponse=$apiResponse, request=$request, response=$response, isCritical=$isCritical)"
    }

    // toReportableError(): mapping to ErrorReportRequest.Error was used by
    // the szkolny.eu crash-reporting endpoint. With SzkolnyApi removed there
    // are no remote consumers, so the conversion was deleted alongside the
    // ErrorReportRequest DTO. Surface errors locally via toString() instead.
}
