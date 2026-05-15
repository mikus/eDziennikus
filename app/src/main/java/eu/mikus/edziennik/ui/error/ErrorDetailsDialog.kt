/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-16.
 */

package eu.mikus.edziennik.ui.error

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.ui.dialogs.base.BaseDialog

class ErrorDetailsDialog(
    activity: AppCompatActivity,
    private val errors: List<ApiError>,
    private val titleRes: Int = R.string.dialog_error_details_title,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BaseDialog<Any>(activity, onShowListener, onDismissListener) {

    override val TAG = "ErrorDetailsDialog"

    override fun getTitleRes() = titleRes
    override fun getMessage() = errors.map {
        listOf(
            it.getStringReason(activity)
                .asBoldSpannable()
                .asColoredSpannable(R.attr.colorOnBackground.resolveAttr(activity)),
            activity.getString(R.string.error_unknown_format, it.errorCode, it.tag),
            if (App.devMode)
                it.throwable?.stackTraceString ?: it.throwable?.localizedMessage
            else
                it.throwable?.localizedMessage
        ).concat("\n")
    }.concat("\n\n")

    override fun isCancelable() = false
    override fun getPositiveButtonText() = R.string.close

    override suspend fun onShow() = Unit

    override suspend fun onBeforeShow(): Boolean {
        return errors.isNotEmpty()
    }
}
