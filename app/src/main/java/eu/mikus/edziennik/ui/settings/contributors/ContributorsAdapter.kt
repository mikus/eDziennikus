/*
 * Copyright (c) Kuba Szczodrzyński 2021-9-7.
 */

package eu.mikus.edziennik.ui.settings.contributors

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.PluralsRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.szkolny.response.ContributorsResponse
import eu.mikus.edziennik.databinding.ContributorsListItemBinding
import eu.mikus.edziennik.ext.plural
import eu.mikus.edziennik.ext.setText
import eu.mikus.edziennik.utils.Utils

class ContributorsAdapter(
    val activity: AppCompatActivity,
    val items: List<ContributorsResponse.Item>,
    @PluralsRes
    val quantityPluralRes: Int
) : RecyclerView.Adapter<ContributorsAdapter.ViewHolder>() {
    companion object {
        private const val TAG = "ContributorsAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = ContributorsListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.b

        b.text.text = item.name ?: item.login
        b.subtext.setText(
            R.string.contributors_subtext_format,
            item.login,
            activity.plural(
                quantityPluralRes,
                item.contributions ?: 0
            )
        )

        b.image.load(item.avatarUrl) {
            transformations(CircleCropTransformation())
        }

        b.root.setOnClickListener {
            Utils.openUrl(activity, item.itemUrl)
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(val b: ContributorsListItemBinding) : RecyclerView.ViewHolder(b.root)
}
