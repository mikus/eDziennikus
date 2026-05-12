/*
 * Copyright (c) Kuba Szczodrzyński 2025-11-25.
 */

package eu.mikus.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import it.sephiroth.android.library.numberpicker.doOnStopTrackingTouch
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.HomeConfigDialogBinding
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ui.dialogs.base.ConfigDialog
import eu.mikus.edziennik.ui.home.HomeCardsDialog

class HomeConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ConfigDialog<HomeConfigDialogBinding>(
    activity,
    reloadOnDismiss,
    onShowListener,
    onDismissListener,
) {

    override val TAG = "HomeConfigDialog"

    override fun getTitleRes() = R.string.menu_home_config
    override fun inflate(layoutInflater: LayoutInflater) =
        HomeConfigDialogBinding.inflate(layoutInflater)

    override suspend fun loadConfig() {
        b.lockCards.isChecked = app.profile.config.ui.homeCardsLocked
        b.eventsLimit.progress = app.profile.config.ui.homeEventsLimit.toFloat()
        b.eventsWeeks.progress = app.profile.config.ui.homeEventsWeeks.toFloat()
        b.gradesWeeks.progress = app.profile.config.ui.homeGradesWeeks.toFloat()
    }

    override suspend fun saveConfig() {
        app.profile.config.ui.homeCardsLocked = b.lockCards.isChecked
        app.profile.config.ui.homeEventsLimit = b.eventsLimit.progress.toInt()
        app.profile.config.ui.homeEventsWeeks = b.eventsWeeks.progress.toInt()
        app.profile.config.ui.homeGradesWeeks = b.gradesWeeks.progress.toInt()
    }

    override fun initView() {
        b.configureCards.onClick {
            HomeCardsDialog(activity, reloadOnDismiss = false).show()
        }

        // who the hell named those methods
        // THIS SHIT DOES NOT EVEN WORK
        b.eventsLimit.doOnStopTrackingTouch {
            app.profile.config.ui.homeEventsLimit = it.progress.toInt()
        }
        b.eventsWeeks.doOnStopTrackingTouch {
            app.profile.config.ui.homeEventsWeeks = it.progress.toInt()
        }
        b.gradesWeeks.doOnStopTrackingTouch {
            app.profile.config.ui.homeGradesWeeks = it.progress.toInt()
        }
    }
}
