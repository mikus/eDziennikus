/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-19.
 */

package eu.mikus.edziennik.ui.dialogs.sync

import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.BaseDialog

class ServerMessageDialog(
    activity: AppCompatActivity,
    private val titleText: String,
    private val messageText: CharSequence,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BaseDialog<Any>(activity, onShowListener, onDismissListener) {

    override val TAG = "ServerMessageDialog"

    override fun getTitle() = titleText
    override fun getTitleRes(): Int? = null
    override fun getMessage() = messageText
    override fun getPositiveButtonText() = R.string.close

    override suspend fun onShow() = Unit
}
