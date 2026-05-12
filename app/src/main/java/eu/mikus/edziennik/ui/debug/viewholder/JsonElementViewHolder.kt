/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-12.
 */

package eu.mikus.edziennik.ui.debug.viewholder

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import eu.mikus.edziennik.App
import eu.mikus.edziennik.databinding.LabItemElementBinding
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.ui.debug.LabJsonAdapter
import eu.mikus.edziennik.ui.debug.models.LabJsonElement
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder

class JsonElementViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        val b: LabItemElementBinding = LabItemElementBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<LabJsonElement, LabJsonAdapter> {
    companion object {
        private const val TAG = "JsonObjectViewHolder"
    }

    @SuppressLint("SetTextI18n")
    override fun onBind(activity: AppCompatActivity, app: App, item: LabJsonElement, position: Int, adapter: LabJsonAdapter) {
        b.root.setPadding(item.level * 8.dp + 8.dp, 8.dp, 8.dp, 8.dp)

        b.type.text = when (item.jsonElement) {
            is JsonPrimitive -> when {
                item.jsonElement.isNumber -> "Number"
                item.jsonElement.isString -> "String"
                item.jsonElement.isBoolean -> "Boolean"
                else -> "Primitive"
            }
            is JsonNull -> "null"
            else -> null
        }

        val colorSecondary = android.R.attr.textColorSecondary.resolveAttr(activity)
        b.key.text = listOf(
                item.key
                    .substringAfterLast(":")
                    .asColoredSpannable(colorSecondary),
                ": ",
                item.jsonElement.toString().asItalicSpannable()
        ).concat("")
    }
}
