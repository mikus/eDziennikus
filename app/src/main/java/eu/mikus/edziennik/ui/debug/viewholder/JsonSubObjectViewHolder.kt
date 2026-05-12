/*
 * Copyright (c) Kuba Szczodrzyński 2021-2-26.
 */

package eu.mikus.edziennik.ui.debug.viewholder

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.databinding.LabItemSubObjectBinding
import eu.mikus.edziennik.ext.dp
import eu.mikus.edziennik.ui.attendance.AttendanceAdapter
import eu.mikus.edziennik.ui.debug.LabJsonAdapter
import eu.mikus.edziennik.ui.debug.models.LabJsonObject
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder

class JsonSubObjectViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: LabItemSubObjectBinding = LabItemSubObjectBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<LabJsonObject, LabJsonAdapter> {
    companion object {
        private const val TAG = "JsonSubObjectViewHolder"
    }

    @SuppressLint("SetTextI18n")
    override fun onBind(activity: AppCompatActivity, app: App, item: LabJsonObject, position: Int, adapter: LabJsonAdapter) {
        b.root.setPadding(item.level * 8.dp + 8.dp, 8.dp, 8.dp, 8.dp)

        b.type.text = "Object"

        b.dropdownIcon.rotation = when (item.state) {
            AttendanceAdapter.STATE_CLOSED -> 0f
            else -> 180f
        }

        b.key.text = item.key.substringAfterLast(":")
    }
}
