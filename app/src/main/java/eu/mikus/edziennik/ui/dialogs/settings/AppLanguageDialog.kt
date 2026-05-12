/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-19.
 */

package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.BaseDialog

class AppLanguageDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BaseDialog<Any>(activity, onShowListener, onDismissListener) {
    override val TAG = "AppLanguageDialog"

    override fun getTitleRes() = R.string.app_language_dialog_title
    override fun getMessage() = activity.getString(R.string.app_language_dialog_text)
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    override fun getSingleChoiceItems(): Map<CharSequence, Any> = mapOf(
        R.string.language_system to "",
        R.string.language_polish to "pl",
        R.string.language_english to "en",
        R.string.language_german to "de",
    ).mapKeys { (resId, _) -> activity.getString(resId) }

    override fun getDefaultSelectedItem() = app.config.ui.language

    override suspend fun onShow() = Unit

    override suspend fun onPositiveClick(): Boolean {
        val language = getSingleSelection() as? String ?: return DISMISS
        if (language.isEmpty())
            app.config.ui.language = null
        else
            app.config.ui.language = language
        activity.recreate()
        return NO_DISMISS
    }
}
