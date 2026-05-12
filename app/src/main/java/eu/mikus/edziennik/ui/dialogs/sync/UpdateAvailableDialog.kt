/*
 * Copyright (c) Kuba Szczodrzyński 2020-9-3.
 */

package eu.mikus.edziennik.ui.dialogs.sync

import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.szkolny.response.Update
import eu.mikus.edziennik.ext.Intent
import eu.mikus.edziennik.sync.UpdateDownloaderService
import eu.mikus.edziennik.ui.dialogs.base.BaseDialog
import eu.mikus.edziennik.utils.Utils
import eu.mikus.edziennik.utils.html.BetterHtml

class UpdateAvailableDialog(
    activity: AppCompatActivity,
    private val update: Update?,
    private val mandatory: Boolean = update?.updateMandatory ?: false,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BaseDialog<Any>(activity, onShowListener, onDismissListener) {

    override val TAG = "UpdateAvailableDialog"

    override fun getTitleRes() = R.string.update_available_title
    override fun getMessageFormat(): Pair<Int, List<CharSequence>> {
        if (update != null) {
            return R.string.update_available_format to listOf(
                BuildConfig.VERSION_NAME,
                update.versionName,
                update.releaseNotes?.let { BetterHtml.fromHtml(activity, it) } ?: "---",
            )
        }
        return R.string.update_available_fallback to emptyList()
    }

    override fun isCancelable() = !mandatory
    override fun getPositiveButtonText() = R.string.update_available_button
    override fun getNeutralButtonText() = if (mandatory) null else R.string.update_available_later

    override suspend fun onShow() = Unit

    override suspend fun onPositiveClick(): Boolean {
        if (update == null || update.isOnGooglePlay)
            Utils.openGooglePlay(activity)
        else
            activity.startService(Intent(app, UpdateDownloaderService::class.java))
        return DISMISS
    }

    override suspend fun onBeforeShow(): Boolean {
        // show only if app is older than available
        return update == null || update.versionCode > BuildConfig.VERSION_CODE
    }
}
