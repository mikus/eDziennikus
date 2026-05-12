/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-30.
 */

package eu.mikus.edziennik.ui.template

import androidx.fragment.app.FragmentManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import eu.mikus.edziennik.ui.base.lazypager.LazyPagerAdapter

class TemplatePagerAdapter(fragmentManager: FragmentManager, swipeRefreshLayout: SwipeRefreshLayout) : LazyPagerAdapter(fragmentManager, swipeRefreshLayout) {
    override fun getPage(position: Int) = TemplatePageFragment()
    override fun getPageTitle(position: Int) = "Page $position"
    override fun getCount() = 10
}
