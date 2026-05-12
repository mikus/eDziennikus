package eu.mikus.edziennik.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.colorRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notification
import eu.mikus.edziennik.databinding.NotificationsListItemBinding
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.utils.models.Date
import kotlin.coroutines.CoroutineContext

class NotificationsAdapter(
        private val activity: AppCompatActivity,
        val onItemClick: ((item: Notification) -> Unit)? = null
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>(), CoroutineScope {
    companion object {
        private const val TAG = "NotificationsAdapter"
    }

    private val app = activity.applicationContext as App
    // optional: place the manager here

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    var items = listOf<Notification>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(activity)
        val view = NotificationsListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.b

        val date = Date.fromMillis(item.addedDate).formattedString
        val colorSecondary = android.R.attr.textColorSecondary.resolveAttr(activity)

        b.notificationIcon.background = IconicsDrawable(app, item.type.icon).apply {
            colorRes = R.color.colorPrimary
        }

        b.title.text = item.text
        b.profileDate.text = listOf(
                item.profileName ?: "",
                " • ",
                date
        ).concat().asColoredSpannable(colorSecondary)
        b.type.text = item.type.titleRes.resolveString(activity)

        onItemClick?.let { listener ->
            b.root.onClick { listener(item) }
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(val b: NotificationsListItemBinding) : RecyclerView.ViewHolder(b.root)
}
