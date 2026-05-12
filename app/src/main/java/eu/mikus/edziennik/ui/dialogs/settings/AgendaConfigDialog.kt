/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-10.
 */

package eu.mikus.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.databinding.DialogConfigAgendaBinding
import eu.mikus.edziennik.ext.onChange
import eu.mikus.edziennik.ui.dialogs.base.ConfigDialog

class AgendaConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ConfigDialog<DialogConfigAgendaBinding>(
    activity,
    reloadOnDismiss,
    onShowListener,
    onDismissListener,
) {

    override val TAG = "AgendaConfigDialog"

    override fun getTitleRes() = R.string.menu_agenda_config
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogConfigAgendaBinding.inflate(layoutInflater)

    override suspend fun loadConfig() {
        b.config = app.profile.config
        b.isAgendaMode = app.profile.config.ui.agendaViewType == Profile.AGENDA_DEFAULT

        var calledFromListener = false
        b.eventSharingEnabled.isChecked = app.profile.canShare
        b.shareByDefault.isEnabled = app.profile.canShare
        b.eventSharingEnabled.onChange { _, isChecked ->
            if (calledFromListener) {
                calledFromListener = false
                return@onChange
            }
            b.eventSharingEnabled.isChecked = !isChecked
            val dialog = RegistrationConfigDialog(
                activity,
                app.profile,
                onChangeListener = { enabled ->
                    calledFromListener = true
                    b.eventSharingEnabled.isChecked = enabled
                    b.shareByDefault.isEnabled = enabled
                },
                onShowListener,
                onDismissListener,
            )
            if (isChecked)
                dialog.showEnableDialog()
            else
                dialog.showDisableDialog()
            return@onChange
        }
    }
}
