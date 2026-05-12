/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-29.
 */

package eu.mikus.edziennik.ui.grades.viewholder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.GradesItemSemesterBinding
import eu.mikus.edziennik.ext.setText
import eu.mikus.edziennik.ui.grades.GradesAdapter
import eu.mikus.edziennik.ui.grades.models.GradesSemester
import eu.mikus.edziennik.ui.grades.models.GradesSubject

class SemesterViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: GradesItemSemesterBinding = GradesItemSemesterBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<GradesSemester, GradesAdapter> {
    companion object {
        private const val TAG = "SemesterViewHolder"
    }

    override fun onBind(activity: AppCompatActivity, app: App, item: GradesSemester, position: Int, adapter: GradesAdapter) {
        val manager = app.gradesManager
        b.semesterName.setText(R.string.grades_semester_format, item.number)
        b.dropdownIcon.rotation = when (item.state) {
            GradesAdapter.STATE_CLOSED -> 0f
            else -> 180f
        }

        b.unread.isVisible = item.hasUnseen

        var unseenChanged = false
        if (item.proposedGrade?.seen == false) {
            manager.markAsSeen(item.proposedGrade!!)
            unseenChanged = true
        }
        if (item.finalGrade?.seen == false) {
            manager.markAsSeen(item.finalGrade!!)
            unseenChanged = true
        }

        if (unseenChanged) {
            val subject = adapter.items.firstOrNull {
                it is GradesSubject && it.subjectId == item.subjectId
            } as? GradesSubject ?: return

            item.hasUnseen = item.grades.any { !it.seen }
            // check if the unseen status has changed
            if (!item.hasUnseen) {
                adapter.notifyItemChanged(item)
            }
            if (!subject.hasUnseen) {
                adapter.notifyItemChanged(subject)
            }
        }

        b.editButton.isVisible = !item.hideEditor

        b.average.text = manager.getAverageString(app, item.averages)
        b.proposedGrade.setGrade(item.proposedGrade, manager)
        b.finalGrade.setGrade(item.finalGrade, manager)
    }
}
