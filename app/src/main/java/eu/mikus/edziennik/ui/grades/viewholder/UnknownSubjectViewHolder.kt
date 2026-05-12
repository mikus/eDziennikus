/*
 * Copyright (c) Kuba Szczodrzyński 2022-3-14.
 */

package eu.mikus.edziennik.ui.grades.viewholder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.databinding.GradesItemUnknownSubjectBinding
import eu.mikus.edziennik.ui.grades.GradesAdapter
import eu.mikus.edziennik.ui.grades.models.GradesUnknownSubject

class UnknownSubjectViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: GradesItemUnknownSubjectBinding = GradesItemUnknownSubjectBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<GradesUnknownSubject, GradesAdapter> {
    companion object {
        private const val TAG = "UnknownSubjectViewHolder"
    }

    override fun onBind(activity: AppCompatActivity, app: App, item: GradesUnknownSubject, position: Int, adapter: GradesAdapter) {

    }
}

