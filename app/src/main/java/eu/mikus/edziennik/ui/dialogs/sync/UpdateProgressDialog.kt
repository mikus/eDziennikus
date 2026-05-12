/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-22.
 */

package eu.mikus.edziennik.ui.dialogs.sync

import android.app.DownloadManager
import android.database.CursorIndexOutOfBoundsException
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import kotlinx.coroutines.Job
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.szkolny.response.Update
import eu.mikus.edziennik.databinding.UpdateProgressDialogBinding
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.startCoroutineTimer
import eu.mikus.edziennik.sync.UpdateStateEvent
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.utils.Utils

class UpdateProgressDialog(
    activity: AppCompatActivity,
    private val update: Update,
    private val downloadId: Long,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<UpdateProgressDialogBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "UpdateProgressDialog"

    override fun getTitleRes() = R.string.notification_downloading_update
    override fun inflate(layoutInflater: LayoutInflater) =
        UpdateProgressDialogBinding.inflate(layoutInflater)

    override fun isCancelable() = false
    override fun getNegativeButtonText() = R.string.cancel

    private var timerJob: Job? = null

    override suspend fun onShow() {
        EventBus.getDefault().register(this)
        b.update = update
        b.progress.progress = 0

        val downloadManager = app.getSystemService<DownloadManager>() ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)

        timerJob?.cancel()
        timerJob = startCoroutineTimer(repeatMillis = 100L) {
            try {
                val cursor = downloadManager.query(query)
                cursor.moveToFirst()
                val progress = cursor.getInt(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    ?.toFloat() ?: return@startCoroutineTimer
                b.downloadedSize.text = Utils.readableFileSize(progress.toLong())
                val total = cursor.getInt(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    ?.toFloat() ?: return@startCoroutineTimer
                b.totalSize.text = Utils.readableFileSize(total.toLong())
                b.progress.progress = (progress / total * 100.0f).toInt()
            } catch (_: CursorIndexOutOfBoundsException) {}
        }
    }

    override fun onDismiss() {
        EventBus.getDefault().unregister(this)
        timerJob?.cancel()
    }

    override suspend fun onNegativeClick(): Boolean {
        val downloadManager = app.getSystemService<DownloadManager>() ?: return NO_DISMISS
        downloadManager.remove(downloadId)
        return DISMISS
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onUpdateStateEvent(event: UpdateStateEvent) {
        if (event.downloadId != downloadId)
            return
        EventBus.getDefault().removeStickyEvent(event)
        if (!event.running)
            dismiss()
    }
}
