/*
 * Copyright (c) Kuba Szczodrzyński 2020-9-3.
 */

package eu.mikus.edziennik.ui.dialogs.sync

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import coil.load
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.szkolny.response.RegisterAvailabilityStatus
import eu.mikus.edziennik.databinding.DialogRegisterUnavailableBinding
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.utils.Utils

class RegisterUnavailableDialog(
    activity: AppCompatActivity,
    private val status: RegisterAvailabilityStatus,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<DialogRegisterUnavailableBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "RegisterUnavailableDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogRegisterUnavailableBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close

    override suspend fun onBeforeShow(): Boolean {
        if (!status.available && status.userMessage != null)
            return true

        if (status.minVersionCode <= BuildConfig.VERSION_CODE)
            return false

        val update = app.config.update
        UpdateAvailableDialog(
            activity = activity,
            update = update,
            mandatory = update != null && update.versionCode >= status.minVersionCode,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener
        ).show()
        return false
    }

    override suspend fun onShow() {
        b.message = status.userMessage ?: return
        b.text.movementMethod = LinkMovementMethod.getInstance()

        if (status.userMessage.image != null) {
            b.image.load(status.userMessage.image)
        }
        if (status.userMessage.url != null) {
            b.readMore.onClick {
                Utils.openUrl(activity, status.userMessage.url)
            }
        }
    }
}
