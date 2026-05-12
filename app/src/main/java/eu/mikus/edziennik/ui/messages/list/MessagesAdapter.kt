package eu.mikus.edziennik.ui.messages.list

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.ui.search.SearchableAdapter

class MessagesAdapter(
    val activity: AppCompatActivity,
    val teachers: List<Teacher>,
    val showNotes: Boolean = true,
    val onMessageClick: ((item: MessageFull) -> Unit)? = null,
    val onStarClick: ((item: MessageFull) -> Unit)? = null,
) : SearchableAdapter<MessageFull>() {
    companion object {
        private const val TAG = "MessagesAdapter"
        private const val ITEM_TYPE_MESSAGE = 0
    }

    private val app = activity.applicationContext as App

    // optional: place the manager here
    internal val manager
        get() = app.messageManager

    val typefaceNormal: Typeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
    val typefaceBold: Typeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    override fun getItemViewType(item: MessageFull) = ITEM_TYPE_MESSAGE

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        item: MessageFull,
    ) {
        if (holder !is MessageViewHolder)
            return
        holder.onBind(activity, app, item, position, this)
    }

    override fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int,
    ) = MessageViewHolder(inflater, parent)
}
