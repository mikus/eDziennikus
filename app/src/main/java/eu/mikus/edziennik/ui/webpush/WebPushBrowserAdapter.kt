/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-19.
 */

package eu.mikus.edziennik.ui.webpush

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.szkolny.response.WebPushResponse
import eu.mikus.edziennik.databinding.WebPushBrowserItemBinding
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ext.setText

class WebPushBrowserAdapter(
        val context: Context,
        val onItemClick: ((browser: WebPushResponse.Browser) -> Unit)? = null,
        val onUnpairButtonClick: ((browser: WebPushResponse.Browser) -> Unit)? = null
) : RecyclerView.Adapter<WebPushBrowserAdapter.ViewHolder>() {

    private val app by lazy { context.applicationContext as App }

    var items = listOf<WebPushResponse.Browser>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = WebPushBrowserItemBinding.inflate(inflater, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val browser = items[position]
        val b = holder.b

        onItemClick?.let { listener ->
            b.root.onClick { listener(browser) }
        }

        b.browserName.text = browser.userAgent
        b.datePaired.setText(R.string.web_push_date_paired_format, browser.dateRegistered)

        onUnpairButtonClick?.let { listener ->
            b.unpair.onClick { listener(browser) }
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(val b: WebPushBrowserItemBinding) : RecyclerView.ViewHolder(b.root)
}
