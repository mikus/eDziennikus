/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-1.
 */

package eu.mikus.edziennik.ui.grades.viewholder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.databinding.GradesItemEmptyBinding
import eu.mikus.edziennik.ui.grades.GradesAdapter
import eu.mikus.edziennik.ui.grades.models.GradesEmpty

class EmptyViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: GradesItemEmptyBinding = GradesItemEmptyBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<GradesEmpty, GradesAdapter> {
    companion object {
        private const val TAG = "EmptyViewHolder"
    }

    override fun onBind(activity: AppCompatActivity, app: App, item: GradesEmpty, position: Int, adapter: GradesAdapter) {

    }
}
