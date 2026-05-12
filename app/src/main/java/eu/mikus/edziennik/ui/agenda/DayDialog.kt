/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-16.
 */

package eu.mikus.edziennik.ui.agenda

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.*
import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.databinding.DialogDayBinding
import eu.mikus.edziennik.ext.ifNotEmpty
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ext.setText
import eu.mikus.edziennik.ui.agenda.lessonchanges.LessonChangesDialog
import eu.mikus.edziennik.ui.agenda.lessonchanges.LessonChangesEvent
import eu.mikus.edziennik.ui.agenda.lessonchanges.LessonChangesEventRenderer
import eu.mikus.edziennik.ui.agenda.teacherabsence.TeacherAbsenceDialog
import eu.mikus.edziennik.ui.agenda.teacherabsence.TeacherAbsenceEvent
import eu.mikus.edziennik.ui.agenda.teacherabsence.TeacherAbsenceEventRenderer
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.ui.event.EventDetailsDialog
import eu.mikus.edziennik.ui.event.EventListAdapter
import eu.mikus.edziennik.ui.event.EventManualDialog
import eu.mikus.edziennik.ui.notes.setupNotesButton
import eu.mikus.edziennik.utils.SimpleDividerItemDecoration
import eu.mikus.edziennik.utils.managers.NoteManager
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.models.Week

class DayDialog(
    activity: AppCompatActivity,
    private val profileId: Int,
    private val date: Date,
    private val eventTypeId: Long? = null,
    private val showNotes: Boolean = false,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<DialogDayBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "DayDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogDayBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun getNeutralButtonText() = R.string.add

    private lateinit var adapter: EventListAdapter

    override suspend fun onNeutralClick(): Boolean {
        EventManualDialog(
            activity,
            profileId,
            defaultDate = date,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener
        ).show()
        return NO_DISMISS
    }

    override suspend fun onShow() {
        b.dayDate.setText(
            R.string.dialog_day_date_format,
            Week.getFullDayName(date.weekDay),
            date.formattedString
        )

        val lessons = withContext(Dispatchers.Default) {
            app.db.timetableDao().getAllForDateNow(profileId, date)
        }.filter { it.type != Lesson.TYPE_NO_LESSONS }

        if (lessons.isNotEmpty()) {
            run {
                val startTime = lessons.first().startTime ?: return@run
                val endTime = lessons.last().endTime ?: return@run
                val diff = Time.diff(startTime, endTime)

                b.lessonsInfo.setText(
                    R.string.dialog_day_lessons_info,
                    startTime.stringHM,
                    endTime.stringHM,
                    lessons.size.toString(),
                    diff.hour.toString(),
                    diff.minute.toString()
                )

                b.lessonsInfo.visibility = View.VISIBLE
            }
        }

        val lessonChanges = withContext(Dispatchers.Default) {
            app.db.timetableDao().getChangesForDateNow(profileId, date)
        }

        lessonChanges.ifNotEmpty {
            LessonChangesEventRenderer().render(
                b.lessonChanges, LessonChangesEvent(
                    profileId = profileId,
                    date = date,
                    count = it.size,
                    showBadge = false
                )
            )

            b.lessonChangesFrame.onClick {
                LessonChangesDialog(
                    activity,
                    profileId,
                    date,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener
                ).show()
            }
        }
        b.lessonChangesFrame.isVisible = lessonChanges.isNotEmpty()

        val teacherAbsences = withContext(Dispatchers.Default) {
            app.db.teacherAbsenceDao().getAllByDateNow(profileId, date)
        }

        teacherAbsences.ifNotEmpty {
            TeacherAbsenceEventRenderer().render(
                b.teacherAbsence, TeacherAbsenceEvent(
                    profileId = profileId,
                    date = date,
                    count = it.size
                )
            )

            b.teacherAbsenceFrame.onClick {
                TeacherAbsenceDialog(
                    activity,
                    profileId,
                    date,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener
                ).show()
            }
        }
        b.teacherAbsenceFrame.isVisible = teacherAbsences.isNotEmpty()

        adapter = EventListAdapter(
            activity = activity,
            showWeekDay = false,
            showDate = false,
            showType = true,
            showTime = true,
            showSubject = true,
            markAsSeen = true,
            onEventClick = {
                EventDetailsDialog(
                    activity,
                    it,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener
                ).show()
            },
            onEventEditClick = {
                EventManualDialog(
                    activity,
                    it.profileId,
                    editingEvent = it,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener
                ).show()
            }
        )

        app.db.eventDao().getAllByDate(profileId, date).observe(activity) { events ->
            events.forEach {
                it.filterNotes()
            }

            adapter.setAllItems(
                if (eventTypeId != null)
                    events.filter { it.type == eventTypeId }
                else
                    events,
            )

            if (b.eventsView.adapter == null) {
                b.eventsView.adapter = adapter
                b.eventsView.apply {
                    isNestedScrollingEnabled = false
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(context)
                    addItemDecoration(SimpleDividerItemDecoration(context))
                }
            }
            adapter.notifyDataSetChanged()

            if (events != null && events.isNotEmpty()) {
                b.eventsView.visibility = View.VISIBLE
                b.eventsNoData.visibility = View.GONE
            } else {
                b.eventsView.visibility = View.GONE
                b.eventsNoData.visibility = View.VISIBLE
            }
        }

        b.notesButton.isVisible = showNotes
        b.notesButton.setupNotesButton(
            activity = activity,
            owner = date,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        )
        b.legend.isVisible = showNotes
        if (showNotes)
            NoteManager.setLegendText(date, b.legend)
    }
}
