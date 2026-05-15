/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-17.
 */

package eu.mikus.edziennik.ext

import im.wangchao.mhttp.Response
import okhttp3.RequestBody
import okio.Buffer
import eu.mikus.edziennik.data.api.*
import eu.mikus.edziennik.data.api.models.ApiError
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun RequestBody.bodyToString(): String {
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readUtf8()
}

fun Response.toErrorCode() = when (this.code()) {
    400 -> ERROR_REQUEST_HTTP_400
    401 -> ERROR_REQUEST_HTTP_401
    403 -> ERROR_REQUEST_HTTP_403
    404 -> ERROR_REQUEST_HTTP_404
    405 -> ERROR_REQUEST_HTTP_405
    410 -> ERROR_REQUEST_HTTP_410
    424 -> ERROR_REQUEST_HTTP_424
    500 -> ERROR_REQUEST_HTTP_500
    503 -> ERROR_REQUEST_HTTP_503
    else -> null
}

fun Throwable.toErrorCode() = when (this) {
    is UnknownHostException -> ERROR_REQUEST_FAILURE_HOSTNAME_NOT_FOUND
    is SSLException -> ERROR_REQUEST_FAILURE_SSL_ERROR
    is SocketTimeoutException -> ERROR_REQUEST_FAILURE_TIMEOUT
    is InterruptedIOException, is ConnectException -> ERROR_REQUEST_FAILURE_NO_INTERNET
    else -> null
}

fun Throwable.toApiError(tag: String) = ApiError.fromThrowable(tag, this)
