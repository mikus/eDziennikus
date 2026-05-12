/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-15.
 */

package eu.mikus.edziennik.ui.captcha

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.RecaptchaViewBinding
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog

class RecaptchaPromptDialog(
    activity: AppCompatActivity,
    private val siteKey: String,
    private val referer: String,
    private val onSuccess: (recaptchaCode: String) -> Unit,
    private val onCancel: (() -> Unit)?,
    private val onServerError: (() -> Unit)? = null,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<RecaptchaViewBinding>(activity, onShowListener, onDismissListener) {
    override val TAG = "RecaptchaPromptDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        RecaptchaViewBinding.inflate(layoutInflater)

    override fun getNegativeButtonText() = R.string.cancel

    private lateinit var checkboxBackground: Drawable
    private lateinit var checkboxForeground: Drawable
    private var success = false

    override suspend fun onShow() {
        checkboxBackground = b.checkbox.background
        checkboxForeground = b.checkbox.foreground
        success = false

        b.root.onClick {
            b.checkbox.performClick()
        }
        b.checkbox.onClick {
            b.checkbox.background = null
            b.checkbox.foreground = null
            b.progress.visibility = View.VISIBLE
            RecaptchaDialog(
                activity,
                siteKey = siteKey,
                referer = referer,
                onSuccess = { recaptchaCode ->
                    b.checkbox.background = checkboxBackground
                    b.checkbox.foreground = checkboxForeground
                    b.progress.visibility = View.GONE
                    success = true
                    onSuccess(recaptchaCode)
                    dialog.dismiss()
                },
                onFailure = {
                    b.checkbox.background = checkboxBackground
                    b.checkbox.foreground = checkboxForeground
                    b.progress.visibility = View.GONE
                },
                onServerError = onServerError,
            ).show()
        }
    }

    override fun onDismiss() {
        if (!success)
            onCancel?.invoke()
    }
}
