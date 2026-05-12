/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-29.
 */

package eu.mikus.edziennik.ui.grades.viewholder

import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.App

interface BindableViewHolder<T, A> {
    fun onBind(activity: AppCompatActivity, app: App, item: T, position: Int, adapter: A)
}
