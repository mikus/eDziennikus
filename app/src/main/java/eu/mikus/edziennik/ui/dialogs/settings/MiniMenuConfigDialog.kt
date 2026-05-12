/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-18.
 */

package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ext.resolveString
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.base.enums.NavTargetLocation
import eu.mikus.edziennik.ui.dialogs.base.BaseDialog

class MiniMenuConfigDialog(
    activity: AppCompatActivity,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BaseDialog<NavTarget>(activity, onShowListener, onDismissListener) {

    override val TAG = "BellSyncTimeChooseDialog"

    override fun getTitleRes() = R.string.settings_theme_mini_drawer_buttons_dialog_title
    override fun getMessageRes() = R.string.settings_theme_mini_drawer_buttons_dialog_text
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    @Suppress("USELESS_CAST")
    override fun getMultiChoiceItems() = NavTarget.values()
        .filter {
            (!it.devModeOnly || App.devMode) && it.location in listOf(
                NavTargetLocation.DRAWER,
                // NavTargetLocation.DRAWER_MORE,
                NavTargetLocation.DRAWER_BOTTOM,
            )
        }
        .associateBy { it.nameRes.resolveString(activity) as CharSequence }

    override fun getDefaultSelectedItems() = app.config.ui.miniMenuButtons

    override suspend fun onShow() = Unit

    override suspend fun onPositiveClick(): Boolean {
        app.config.ui.miniMenuButtons = getMultiSelection()
        if (activity is MainActivity) {
            activity.setDrawerItems()
            activity.drawer.updateBadges()
        }
        return DISMISS
    }
}
