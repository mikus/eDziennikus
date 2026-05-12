/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package eu.mikus.edziennik.ui.agenda.teacherabsence

import android.view.View
import androidx.core.view.isVisible
import com.github.tibolte.agendacalendarview.render.EventRenderer
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.AgendaCounterItemBinding
import eu.mikus.edziennik.databinding.AgendaWrappedCounterBinding
import eu.mikus.edziennik.ext.setTintColor
import eu.mikus.edziennik.utils.Colors

class TeacherAbsenceEventRenderer : EventRenderer<TeacherAbsenceEvent>() {

    override fun render(view: View, event: TeacherAbsenceEvent) {
        val b = AgendaWrappedCounterBinding.bind(view).item
        val textColor = Colors.legibleTextColor(event.color)

        b.card.foreground.setTintColor(event.color)
        b.card.background.setTintColor(event.color)
        b.name.setText(R.string.agenda_teacher_absence)
        b.name.setTextColor(textColor)
        b.count.text = event.count.toString()
        b.count.setTextColor(textColor)

        b.badgeBackground.isVisible = false
        b.badge.isVisible = false
    }

    fun render(b: AgendaCounterItemBinding, event: TeacherAbsenceEvent) {
        val textColor = Colors.legibleTextColor(event.color)

        b.card.foreground.setTintColor(event.color)
        b.card.background.setTintColor(event.color)
        b.name.setText(R.string.agenda_teacher_absence)
        b.name.setTextColor(textColor)
        b.count.text = event.count.toString()
        b.count.setTextColor(textColor)

        b.badgeBackground.isVisible = false
        b.badge.isVisible = false
    }

    override fun getEventLayout(): Int = R.layout.agenda_wrapped_counter
}
