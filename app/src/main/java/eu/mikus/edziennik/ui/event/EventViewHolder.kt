/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-10.
 */

package eu.mikus.edziennik.ui.event

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.databinding.EventListItemBinding
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Week

class EventViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: EventListItemBinding = EventListItemBinding.inflate(inflater, parent, false),
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<EventFull, EventListAdapter> {
    companion object {
        private const val TAG = "EventViewHolder"
    }

    override fun onBind(
        activity: AppCompatActivity,
        app: App,
        item: EventFull,
        position: Int,
        adapter: EventListAdapter,
    ) {
        val manager = app.eventManager

        if (adapter.onEventClick != null) {
            b.root.onClick {
                adapter.onEventClick.invoke(item)
                if (!item.seen) {
                    manager.markAsSeen(item)
                }
                if (item.showAsUnseen == true) {
                    item.showAsUnseen = false
                    adapter.notifyItemChanged(item)
                }
            }
        }

        val bullet = " • "
        val colorHighlight = R.attr.colorControlHighlight.resolveAttr(activity)

        b.addedBy.isVisible = !adapter.simpleMode

        manager.setEventTopic(b.topic, item, showType = false, showNotes = adapter.showNotes)
        b.topic.text = adapter.highlightSearchText(
            item = item,
            text = b.topic.text,
            color = colorHighlight
        )
        b.topic.maxLines = if (adapter.simpleMode) 2 else 3

        // Mirrors EventRow.eventDetails — the two move together until this view holder retires.
        // The weekday arm is deliberately NOT gated on item.dateUnknown: no caller can reach it,
        // because EventListAdapter.kt:23 defaults showWeekDay to false, NoteManager.kt:143-146
        // omits it and DayDialog.kt:151 passes it false — a guard there would be dead code.
        // showDate IS reachable, via the note header (NoteManager.kt:146).
        // The unknown label survives showDate = false: that flag means "the date is implied by the
        // surface", which an unknown date makes false, and the row is filed at today — so staying
        // silent in DayDialog.kt:149 (the only caller here that passes it false, under a title
        // printing the weekday and date) would read as "due today".
        b.details.text = mutableListOf(
            if (adapter.showWeekDay)
                Week.getFullDayName(item.date.weekDay)
            else null,
            if (adapter.showDate || item.dateUnknown)
                when {
                    item.dateUnknown -> app.getString(R.string.event_date_unknown)
                    else -> item.date.getRelativeString(activity, 7) ?: item.date.formattedStringShort
                }
            else null,
            if (adapter.showType && item.typeName != null)
                item.typeName
            else null,
            if (adapter.showTime)
                item.time?.stringHM ?: app.getString(R.string.event_all_day)
            else null,
            if (adapter.showSubject && item.subjectLongName != null)
                adapter.highlightSearchText(
                    item = item,
                    text = item.subjectLongName ?: "",
                    color = colorHighlight
                )
            else null,
        ).concat(bullet)

        val addedBy = item.teacherName ?: ""
        b.addedBy.setText(
            when {
                item.addedManually -> R.string.event_list_added_by_self_format
                item.teacherName == null -> R.string.event_list_added_by_unknown_format
                else -> R.string.event_list_added_by_format
            },
            /* 1$ */
            Date.fromMillis(item.addedDate).formattedString,
            /* 2$ */
            addedBy,
            /* 3$ */
            item.teamName?.let { bullet + it } ?: "",
        )
        // workaround for the span data lost during setText above
        val addedBySpanned = adapter.highlightSearchText(
            item = item,
            text = addedBy,
            color = colorHighlight
        )
        b.addedBy.text = b.addedBy.text.replaceSpanned(addedBy, addedBySpanned)

        b.attachmentIcon.isVisible = item.hasAttachments

        b.typeColor.background?.setTintColor(item.eventColor)
        b.typeColor.isVisible = adapter.showTypeColor

        b.editButton.isVisible = !adapter.simpleMode
                && item.addedManually
                && !item.isDone
                && adapter.onEventEditClick != null

        if (adapter.onEventEditClick != null) {
            b.editButton.onClick {
                adapter.onEventEditClick.invoke(item)
            }
            b.editButton.attachToastHint(R.string.hint_edit_event)
        }

        if (item.showAsUnseen == null)
            item.showAsUnseen = !item.seen

        b.unread.isVisible = item.showAsUnseen == true
        if (adapter.markAsSeen && !item.seen) {
            manager.markAsSeen(item)
        }
    }
}
