/*
 * Copyright (c) Kuba Szczodrzyński 2020-9-3.
 */

package eu.mikus.edziennik.ui.home.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.plusAssign
import androidx.core.view.setMargins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.App
import eu.mikus.edziennik.BuildConfig
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.databinding.CardHomeAvailabilityBinding
import eu.mikus.edziennik.ext.Intent
import eu.mikus.edziennik.ext.dp
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ext.setText
import eu.mikus.edziennik.sync.UpdateDownloaderService
import eu.mikus.edziennik.ui.dialogs.sync.UpdateAvailableDialog
import eu.mikus.edziennik.ui.home.HomeCard
import eu.mikus.edziennik.ui.home.HomeCardAdapter
import eu.mikus.edziennik.ui.home.HomeFragment
import eu.mikus.edziennik.utils.Utils
import kotlin.coroutines.CoroutineContext

class HomeAvailabilityCard(
        override val id: Int,
        val app: App,
        val activity: MainActivity,
        val fragment: HomeFragment,
        val profile: Profile
) : HomeCard, CoroutineScope {
    companion object {
        private const val TAG = "HomeAvailabilityCard"
    }

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun bind(position: Int, holder: HomeCardAdapter.ViewHolder) {
        holder.root.removeAllViews()
        val b = CardHomeAvailabilityBinding.inflate(LayoutInflater.from(holder.root.context))
        b.root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(8.dp)
        }
        holder.root += b.root

        // The provider-availability "register unavailable" path was removed
        // when SzkolnyApi was dropped; this card now only renders when a
        // newer GitHub Release is available.
        val update = app.config.update
        if (update == null || update.versionCode <= BuildConfig.VERSION_CODE) {
            b.root.isVisible = false
            return
        }

        b.homeAvailabilityTitle.setText(R.string.home_availability_title)
        b.homeAvailabilityText.setText(R.string.home_availability_text, update.versionName)
        b.homeAvailabilityUpdate.isVisible = true
        b.homeAvailabilityIcon.setImageResource(R.drawable.ic_update)
        val onInfoClick: (View) -> Unit = {
            UpdateAvailableDialog(activity, update).show()
        }

        b.homeAvailabilityUpdate.onClick {
            if (update == null)
                return@onClick
            if (update.isOnGooglePlay)
                Utils.openGooglePlay(activity)
            else
                activity.startService(Intent(app, UpdateDownloaderService::class.java))
        }

        b.homeAvailabilityInfo.onClick(onInfoClick)
        holder.root.onClick(onInfoClick)
    }

    override fun unbind(position: Int, holder: HomeCardAdapter.ViewHolder) = Unit
}
