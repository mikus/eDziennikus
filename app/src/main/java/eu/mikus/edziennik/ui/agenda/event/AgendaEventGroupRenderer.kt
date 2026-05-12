/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-10.
 */

package eu.mikus.edziennik.ui.agenda.event

import android.view.View
import androidx.core.view.isVisible
import com.github.tibolte.agendacalendarview.render.EventRenderer
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.AgendaWrappedGroupBinding
import eu.mikus.edziennik.ext.resolveAttr
import eu.mikus.edziennik.ext.setTintColor
import eu.mikus.edziennik.utils.Colors

class AgendaEventGroupRenderer : EventRenderer<AgendaEventGroup>() {

    override fun render(view: View, event: AgendaEventGroup) {
        val b = AgendaWrappedGroupBinding.bind(view).item

        b.card.foreground.setTintColor(event.color)
        b.card.background.setTintColor(event.color)
        b.name.text = event.typeName
        b.name.setTextColor(Colors.legibleTextColor(event.color))
        b.count.text = event.count.toString()
        b.count.background.setTintColor(android.R.attr.colorBackground.resolveAttr(view.context))

        b.badge.isVisible = event.showItemBadge
    }

    override fun getEventLayout(): Int = R.layout.agenda_wrapped_group
}

