package eu.mikus.edziennik.data.api.edziennik.librus.data.messages

import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import eu.mikus.edziennik.data.api.*
import eu.mikus.edziennik.data.api.edziennik.librus.DataLibrus
import eu.mikus.edziennik.data.api.edziennik.librus.data.LibrusMessages
import eu.mikus.edziennik.data.api.events.AttachmentGetEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.ext.get
import eu.mikus.edziennik.ext.getString
import eu.mikus.edziennik.utils.PublicDownloads
import eu.mikus.edziennik.utils.Utils
import java.io.File
import kotlin.coroutines.CoroutineContext

class LibrusSandboxDownloadAttachment(override val data: DataLibrus,
                                      downloadLink: String,
                                      val owner: Any,
                                      val attachmentId: Long,
                                      val attachmentName: String,
                                      val onSuccess: () -> Unit
) : LibrusMessages(data, null), CoroutineScope {
    companion object {
        const val TAG = "LibrusSandboxDownloadAttachment"
    }

    private var job = Job()

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

    private var getAttachmentCheckKeyTries = 0

    init {
        val keyMatcher = Regexes.LIBRUS_ATTACHMENT_KEY.find(downloadLink)

        when {
            downloadLink.contains("CSDownloadFailed") -> {
                data.error(ApiError(TAG, ERROR_LIBRUS_MESSAGES_ATTACHMENT_NOT_FOUND))
                onSuccess()
            }
            keyMatcher != null -> {
                getAttachmentCheckKeyTries = 0

                val attachmentKey = keyMatcher[1]
                getAttachmentCheckKey(attachmentKey) {
                    downloadAttachment("${LIBRUS_SANDBOX_URL}CSDownload&singleUseKey=$attachmentKey", method = POST)
                }
            }
            else -> {
                downloadAttachment("$downloadLink/get", method = GET)
            }
        }
    }

    private fun getAttachmentCheckKey(attachmentKey: String, callback: () -> Unit) {
        sandboxGet(TAG, "CSCheckKey",
                parameters = mapOf("singleUseKey" to attachmentKey)) { json ->

            when (json.getString("status")) {
                "not_downloaded_yet" -> {
                    if (getAttachmentCheckKeyTries++ > 5) {
                        data.error(ApiError(TAG, ERROR_FILE_DOWNLOAD)
                                .withApiResponse(json))
                        return@sandboxGet
                    }
                    launch {
                        delay(2000)
                        getAttachmentCheckKey(attachmentKey, callback)
                    }
                }

                "ready" -> {
                    launch { callback() }
                }

                else -> {
                    data.error(ApiError(TAG, EXCEPTION_LIBRUS_MESSAGES_REQUEST)
                            .withApiResponse(json))
                }
            }
        }
    }

    private fun downloadAttachment(url: String, method: Int = GET) {
        val targetFile = File(Utils.getStorageDir(), attachmentName)

        sandboxGetFile(TAG, url, targetFile, { file ->

            val event = AttachmentGetEvent(
                    profileId,
                    owner,
                    attachmentId,
                    AttachmentGetEvent.TYPE_FINISHED,
                    file.absolutePath
            )

            val attachmentDataFile = File(Utils.getStorageDir(), ".${profileId}_${event.ownerId}_${event.attachmentId}")
            Utils.writeStringToFile(attachmentDataFile, event.fileName)

            EventBus.getDefault().postSticky(event)

            // Best-effort user-visible copy. PublicDownloads contains its own failures, which
            // matters here: this lambda runs inside sandboxGetFile's catch-all, where an escaping
            // throwable would surface as a download error.
            PublicDownloads.publish(data.app, file, attachmentName)

            onSuccess()

        }) { written, _ ->
            val event = AttachmentGetEvent(
                    profileId,
                    owner,
                    attachmentId,
                    AttachmentGetEvent.TYPE_PROGRESS,
                    bytesWritten = written
            )

            EventBus.getDefault().postSticky(event)
        }
    }
}
