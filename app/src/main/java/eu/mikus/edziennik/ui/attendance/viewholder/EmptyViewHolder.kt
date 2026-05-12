/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-4.
 */

package eu.mikus.edziennik.ui.attendance.viewholder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.databinding.AttendanceItemEmptyBinding
import eu.mikus.edziennik.ui.attendance.AttendanceAdapter
import eu.mikus.edziennik.ui.attendance.models.AttendanceEmpty
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder

class EmptyViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        val b: AttendanceItemEmptyBinding = AttendanceItemEmptyBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<AttendanceEmpty, AttendanceAdapter> {
    companion object {
        private const val TAG = "EmptyViewHolder"
    }

    override fun onBind(activity: AppCompatActivity, app: App, item: AttendanceEmpty, position: Int, adapter: AttendanceAdapter) {

    }
}
