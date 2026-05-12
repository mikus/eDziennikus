/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package eu.mikus.edziennik.ui.agenda.lessonchanges

import android.view.View
import androidx.core.view.isVisible
import com.github.tibolte.agendacalendarview.render.EventRenderer
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.AgendaCounterItemBinding
import eu.mikus.edziennik.databinding.AgendaWrappedCounterBinding
import eu.mikus.edziennik.ext.resolveAttr
import eu.mikus.edziennik.ext.setTintColor
import eu.mikus.edziennik.utils.Colors

class LessonChangesEventRenderer : EventRenderer<LessonChangesEvent>() {

    override fun render(view: View, event: LessonChangesEvent) {
        val b = AgendaWrappedCounterBinding.bind(view).item
        val textColor = Colors.legibleTextColor(event.color)

        b.card.foreground.setTintColor(event.color)
        b.card.background.setTintColor(event.color)
        b.name.setText(R.string.agenda_lesson_changes)
        b.name.setTextColor(textColor)
        b.count.text = event.count.toString()
        b.count.setTextColor(textColor)

        b.badgeBackground.isVisible = event.showItemBadge
        b.badgeBackground.background.setTintColor(
            android.R.attr.colorBackground.resolveAttr(view.context)
        )
        b.badge.isVisible = event.showItemBadge
    }

    fun render(b: AgendaCounterItemBinding, event: LessonChangesEvent) {
        val textColor = Colors.legibleTextColor(event.color)

        b.card.foreground.setTintColor(event.color)
        b.card.background.setTintColor(event.color)
        b.name.setText(R.string.agenda_lesson_changes)
        b.name.setTextColor(textColor)
        b.count.text = event.count.toString()
        b.count.setTextColor(textColor)

        b.badgeBackground.isVisible = event.showItemBadge
        b.badgeBackground.background.setTintColor(
            android.R.attr.colorBackground.resolveAttr(b.root.context)
        )
        b.badge.isVisible = event.showItemBadge
    }

    override fun getEventLayout(): Int = R.layout.agenda_wrapped_counter
}
