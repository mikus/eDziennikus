/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-7.
 */

package eu.mikus.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.TimetableConfigDialogBinding
import eu.mikus.edziennik.ext.Intent
import eu.mikus.edziennik.ui.dialogs.base.ConfigDialog
import eu.mikus.edziennik.ui.timetable.TimetableFragment

class TimetableConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ConfigDialog<TimetableConfigDialogBinding>(
    activity,
    reloadOnDismiss,
    onShowListener,
    onDismissListener,
) {

    override val TAG = "TimetableConfigDialog"

    override fun getTitleRes() = R.string.menu_timetable_config
    override fun inflate(layoutInflater: LayoutInflater) =
        TimetableConfigDialogBinding.inflate(layoutInflater)

    override fun initView() {
        b.features = app.profile.loginStoreType.features
    }

    override suspend fun loadConfig() {
        b.config = app.profile.config.ui
    }

    override suspend fun saveConfig() {
        activity.sendBroadcast(Intent(TimetableFragment.ACTION_RELOAD_PAGES))
    }
}
