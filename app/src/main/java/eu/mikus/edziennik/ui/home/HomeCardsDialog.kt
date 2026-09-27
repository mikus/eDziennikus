/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-11.
 */

package eu.mikus.edziennik.ui.home

import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.BaseDialog
import eu.mikus.edziennik.ui.home.HomeCard.Companion.CARD_EVENTS
import eu.mikus.edziennik.ui.home.HomeCard.Companion.CARD_GRADES
import eu.mikus.edziennik.ui.home.HomeCard.Companion.CARD_LUCKY_NUMBER
import eu.mikus.edziennik.ui.home.HomeCard.Companion.CARD_NOTES
import eu.mikus.edziennik.ui.home.HomeCard.Companion.CARD_TIMETABLE

class HomeCardsDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BaseDialog<Int>(activity, onShowListener, onDismissListener) {

    companion object {
        /**
         * The cards this dialog offers, paired with their labels, in the order its checkboxes are
         * listed. Single source for both the checkbox map and the id list handed to HomeCardOrder —
         * a second, separately maintained ordering here could drift out of step and silently drop
         * cards.
         */
        private val OFFERED_CARDS = listOf(
            R.string.card_type_lucky_number to CARD_LUCKY_NUMBER,
            R.string.card_type_timetable to CARD_TIMETABLE,
            R.string.card_type_grades to CARD_GRADES,
            R.string.card_type_events to CARD_EVENTS,
            R.string.card_type_notes to CARD_NOTES,
        )

        /**
         * Just the card ids of [OFFERED_CARDS], in checkbox order. Named rather than derived inline
         * at the call site so that reaching for the wrong half of the pair — the string resource id
         * instead of the card id — cannot compile. Duplicate-free, as applySelection requires.
         */
        private val OFFERED_IDS = OFFERED_CARDS.map { (_, cardId) -> cardId }
    }

    override val TAG = "HomeCardsDialog"

    override fun getTitleRes() = R.string.home_configure_add_remove
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    override fun getMultiChoiceItems(): Map<CharSequence, Int> =
        OFFERED_CARDS.associate { (resId, cardId) -> activity.getString(resId) to cardId }

    override fun getDefaultSelectedItems() =
        app.profile.config.ui.homeCards
            .filter { it.profileId == App.profileId }
            .map { it.cardId }
            .toSet()

    override suspend fun onShow() = Unit

    private var configChanged = false

    override suspend fun onPositiveClick(): Boolean {
        HomeCardOrder.applySelection(
            all = app.profile.config.ui.homeCards,
            profileId = App.profileId,
            selected = getMultiSelection(),
            offered = OFFERED_IDS,
        )?.let { app.profile.config.ui.homeCards = it }
        return DISMISS
    }

    override suspend fun onMultiSelectionChanged(items: Set<Int>) {
        configChanged = true
    }

    override fun onDismiss() {
        if (configChanged && reloadOnDismiss && activity is MainActivity)
            activity.reloadTarget()
    }
}
