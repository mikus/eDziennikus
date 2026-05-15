/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-10.
 */

package eu.mikus.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.databinding.DialogConfigAgendaBinding
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

        b.eventSharingEnabled.isChecked = app.profile.canShare
        b.shareByDefault.isEnabled = app.profile.canShare
        // Cross-user event sharing was removed when SzkolnyApi was dropped
        // from the fork. The toggle is hidden so users can't enable a
        // feature that no longer works; the underlying switch listener is
        // left as a no-op for completeness.
        b.eventSharingEnabled.isVisible = false
    }
}
