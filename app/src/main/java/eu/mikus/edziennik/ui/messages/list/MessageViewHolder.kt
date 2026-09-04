/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-5.
 */

package eu.mikus.edziennik.ui.messages.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.MessageFull
import eu.mikus.edziennik.databinding.MessagesListItemBinding
import eu.mikus.edziennik.ext.attachToastHint
import eu.mikus.edziennik.ext.detachToastHint
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ext.resolveAttr
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder
import eu.mikus.edziennik.ui.messages.MessagesUtils
import eu.mikus.edziennik.utils.managers.NoteManager
import eu.mikus.edziennik.utils.models.Date

class MessageViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: MessagesListItemBinding = MessagesListItemBinding.inflate(inflater, parent, false),
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<MessageFull, MessagesAdapter> {
    companion object {
        private const val TAG = "MessageViewHolder"
    }

    override fun onBind(
        activity: AppCompatActivity,
        app: App,
        item: MessageFull,
        position: Int,
        adapter: MessagesAdapter,
    ) {
        b.messageDate.text = Date.fromMillis(item.addedDate).formattedStringShort
        b.messageAttachmentImage.isVisible = item.hasAttachments

        b.messageBody.text = item.bodyHtml?.take(200)

        val isRead = item.isSent || item.isDraft || item.seen
        val typeface = if (isRead) adapter.typefaceNormal else adapter.typefaceBold
        val style = if (isRead) R.style.AppText_Small else R.style.AppText_Normal
        // set text styles
        b.messageSender.setTextAppearance(activity, style)
        b.messageSender.typeface = typeface
        b.messageSubject.setTextAppearance(activity, style)
        b.messageSubject.typeface = typeface
        b.messageDate.setTextAppearance(activity, style)
        b.messageDate.typeface = typeface

        if (adapter.onStarClick == null) {
            b.messageStar.isVisible = false
        }
        b.messageStar.detachToastHint()

        val messageInfo = MessagesUtils.getMessageInfo(app, item, 48, 24, 18, 12)
        b.messageProfileBackground.setImageBitmap(messageInfo.profileImage)

        val colorHighlight = R.attr.colorControlHighlight.resolveAttr(activity)
        b.messageSubject.text = adapter.highlightSearchText(
            item = item,
            text = item.subject,
            color = colorHighlight
        )
        b.messageSender.text = adapter.highlightSearchText(
            item = item,
            text = messageInfo.profileName ?: "",
            color = colorHighlight
        )

        if (adapter.showNotes)
            NoteManager.prependIcon(item, b.messageSubject)

        adapter.onMessageClick?.let { listener ->
            b.root.onClick { listener(item) }
        }
        adapter.onStarClick?.let { listener ->
            b.messageStar.isVisible = true
            adapter.manager.setStarIcon(b.messageStar, item)
            b.messageStar.onClick { listener(item) }
            b.messageStar.attachToastHint(R.string.hint_message_star)
        }
    }
}
