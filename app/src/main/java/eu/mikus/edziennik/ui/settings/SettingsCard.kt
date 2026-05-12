/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-17.
 */

package eu.mikus.edziennik.ui.settings

import com.danielstone.materialaboutlibrary.items.MaterialAboutItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity

abstract class SettingsCard(
    protected val util: SettingsUtil,
) {
    protected val app: App = util.activity.application as App
    protected val activity: MainActivity = util.activity

    protected val configGlobal by lazy { app.config }
    protected val configProfile by lazy { app.profile.config }

    val card by lazy {
        buildCard()
    }

    protected abstract fun buildCard(): MaterialAboutCard
    protected abstract fun getItems(card: MaterialAboutCard): List<MaterialAboutItem>
    protected open fun getItemsMore(card: MaterialAboutCard): List<MaterialAboutItem> = listOf()
}
