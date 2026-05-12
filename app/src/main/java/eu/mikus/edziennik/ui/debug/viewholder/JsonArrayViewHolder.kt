/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-12.
 */

package eu.mikus.edziennik.ui.debug.viewholder

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.databinding.LabItemObjectBinding
import eu.mikus.edziennik.ext.dp
import eu.mikus.edziennik.ui.attendance.AttendanceAdapter
import eu.mikus.edziennik.ui.debug.LabJsonAdapter
import eu.mikus.edziennik.ui.debug.models.LabJsonArray
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder

class JsonArrayViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        val b: LabItemObjectBinding = LabItemObjectBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<LabJsonArray, LabJsonAdapter> {
    companion object {
        private const val TAG = "JsonArrayViewHolder"
    }

    @SuppressLint("SetTextI18n")
    override fun onBind(activity: AppCompatActivity, app: App, item: LabJsonArray, position: Int, adapter: LabJsonAdapter) {
        b.root.setPadding(item.level * 8.dp + 8.dp, 8.dp, 8.dp, 8.dp)

        b.type.text = "Array"

        b.dropdownIcon.rotation = when (item.state) {
            AttendanceAdapter.STATE_CLOSED -> 0f
            else -> 180f
        }
        b.previewContainer.isInvisible = item.state != AttendanceAdapter.STATE_CLOSED
        b.summaryContainer.isInvisible = item.state == AttendanceAdapter.STATE_CLOSED

        b.key.text = item.key.substringAfterLast(":")
        b.previewContainer.text = item.jsonArray.toString().take(200)
        b.summaryContainer.text = item.jsonArray.size().toString() + " elements"
    }
}
