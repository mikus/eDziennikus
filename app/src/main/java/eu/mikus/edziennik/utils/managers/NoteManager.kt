/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-17.
 */

package eu.mikus.edziennik.utils.managers

import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.view.IconicsTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.data.db.entity.Note.OwnerType
import eu.mikus.edziennik.data.db.entity.Noteable
import eu.mikus.edziennik.data.db.full.*
import eu.mikus.edziennik.databinding.NoteDialogHeaderBinding
import eu.mikus.edziennik.ext.resolveDrawable
import eu.mikus.edziennik.ui.agenda.DayDialog
import eu.mikus.edziennik.ui.agenda.lessonchanges.LessonChangesAdapter
import eu.mikus.edziennik.ui.announcements.AnnouncementsAdapter
import eu.mikus.edziennik.ui.attendance.AttendanceAdapter
import eu.mikus.edziennik.ui.attendance.AttendanceDetailsDialog
import eu.mikus.edziennik.ui.attendance.AttendanceFragment
import eu.mikus.edziennik.ui.behaviour.NoticesAdapter
import eu.mikus.edziennik.ui.event.EventDetailsDialog
import eu.mikus.edziennik.ui.event.EventListAdapter
import eu.mikus.edziennik.ui.grades.GradeDetailsDialog
import eu.mikus.edziennik.ui.grades.GradesAdapter
import eu.mikus.edziennik.ui.messages.list.MessagesAdapter
import eu.mikus.edziennik.ui.timetable.LessonDetailsDialog
import eu.mikus.edziennik.utils.models.Date

class NoteManager(private val app: App) {
    companion object {
        private const val TAG = "NoteManager"

        @SuppressLint("SetTextI18n")
        fun prependIcon(owner: Noteable, textView: IconicsTextView) {
            if (owner.hasNotes())
                textView.text = SpannableStringBuilder(
                    if (owner.hasReplacingNotes())
                        "{cmd-swap-horizontal} "
                    else
                        "{cmd-playlist-edit} "
                ).append(textView.text)
        }

        fun getLegendText(owner: Noteable): Int? = when {
            owner.hasReplacingNotes() -> R.string.legend_notes_added_replaced
            owner.hasNotes() -> R.string.legend_notes_added
            else -> null
        }

        fun setLegendText(owner: Noteable, textView: IconicsTextView) {
            textView.isVisible = owner.hasNotes()
            textView.setText(getLegendText(owner) ?: return)
        }
    }

    fun getOwner(note: Note): Any? {
        if (note.ownerId == null)
            return null
        return when (note.ownerType) {
            OwnerType.ANNOUNCEMENT ->
                app.db.announcementDao().getByIdNow(note.profileId, note.ownerId)
            OwnerType.ATTENDANCE ->
                app.db.attendanceDao().getByIdNow(note.profileId, note.ownerId)
            OwnerType.BEHAVIOR ->
                app.db.noticeDao().getByIdNow(note.profileId, note.ownerId)
            OwnerType.EVENT ->
                app.db.eventDao().getByIdNow(note.profileId, note.ownerId)
            OwnerType.EVENT_SUBJECT, OwnerType.LESSON_SUBJECT ->
                app.db.subjectDao().getByIdNow(note.profileId, note.ownerId)
            OwnerType.GRADE ->
                app.db.gradeDao().getByIdNow(note.profileId, note.ownerId)
            OwnerType.LESSON ->
                app.db.timetableDao().getByOwnerIdNow(note.profileId, note.ownerId)
            OwnerType.MESSAGE ->
                app.db.messageDao().getByIdNow(note.profileId, note.ownerId)
            else -> null
        }
    }

    fun hasValidOwner(note: Note): Boolean {
        if (note.ownerType == null || note.ownerType == OwnerType.DAY)
            return true
        return getOwner(note) != null
    }

    suspend fun saveNote(
        activity: AppCompatActivity,
        note: Note,
        teamId: Long?,
        wasShared: Boolean,
    ): Boolean {
        val success = when {
            !note.isShared && wasShared -> unshareNote(activity, note)
            note.isShared -> shareNote(activity, note, teamId)
            else -> true
        }

        if (!success)
            return false

        withContext(Dispatchers.IO) {
            app.db.noteDao().add(note)
        }
        return true
    }

    suspend fun deleteNote(activity: AppCompatActivity, note: Note): Boolean {
        val success = when {
            note.isShared -> unshareNote(activity, note)
            else -> true
        }

        if (!success)
            return false

        withContext(Dispatchers.IO) {
            app.db.noteDao().delete(note)
        }
        return true
    }

    // Note sharing across users went through szkolny.eu's backend. After the
    // SzkolnyApi removal these are local-only no-ops; the note's isShared
    // flag continues to be persisted (legacy state remains visible) but no
    // longer round-trips to a server. Both stubs report success so the
    // saveNote() / removeNote() flows continue normally.
    @Suppress("UNUSED_PARAMETER")
    private suspend fun shareNote(activity: AppCompatActivity, note: Note, teamId: Long?): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    private suspend fun unshareNote(activity: AppCompatActivity, note: Note): Boolean = true

    private fun getAdapterForItem(
        activity: AppCompatActivity,
        item: Noteable,
    ): RecyclerView.Adapter<*>? {
        return when (item) {
            is AnnouncementFull -> AnnouncementsAdapter(activity, mutableListOf(item), null)

            is AttendanceFull -> AttendanceAdapter(
                activity,
                showNotes = false,
                onAttendanceClick = {
                    showItemDetailsDialog(activity, it)
                },
                type = AttendanceFragment.VIEW_LIST
            ).also {
                it.items = mutableListOf(item)
            }

            is NoticeFull -> {
                NoticesAdapter(activity, listOf(item))
            }

            is Date -> {
                TODO("Date adapter is not yet implemented.")
            }

            is EventFull -> EventListAdapter(
                activity = activity,
                simpleMode = true,
                showDate = true,
                showTypeColor = false,
                showTime = false,
                markAsSeen = false,
                showNotes = false,
                onEventClick = {
                    showItemDetailsDialog(activity, it)
                },
            ).also {
                it.setAllItems(listOf(item))
            }

            is GradeFull -> GradesAdapter(activity, showNotes = false, onGradeClick = {
                showItemDetailsDialog(activity, it)
            }).also {
                it.items = mutableListOf(item)
            }

            is LessonFull -> LessonChangesAdapter(activity, showNotes = false, onLessonClick = {
                showItemDetailsDialog(activity, it)
            }).also {
                it.items = listOf(item)
            }

            is MessageFull -> MessagesAdapter(
                activity = activity,
                teachers = listOf(),
                showNotes = false,
                onMessageClick = null,
            ).also {
                it.setAllItems(listOf(item))
            }
            else -> null
        }
    }

    private fun showItemDetailsDialog(
        activity: AppCompatActivity,
        item: Noteable,
        onShowListener: ((tag: String) -> Unit)? = null,
        onDismissListener: ((tag: String) -> Unit)? = null,
    ) {
        when (item) {
            is AnnouncementFull -> return
            is AttendanceFull -> AttendanceDetailsDialog(
                activity = activity,
                attendance = item,
                showNotes = false,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            ).show()
            is NoticeFull -> return
            is Date -> DayDialog(
                activity = activity,
                profileId = App.profileId,
                date = item,
                showNotes = false,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            ).show()
            is EventFull -> EventDetailsDialog(
                activity = activity,
                event = item,
                showNotes = false,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            ).show()
            is GradeFull -> GradeDetailsDialog(
                activity = activity,
                grade = item,
                showNotes = false,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            ).show()
            is LessonFull -> LessonDetailsDialog(
                activity = activity,
                lesson = item,
                showNotes = false,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            ).show()
            is MessageFull -> return
        }
    }

    fun getOwnerTypeText(owner: OwnerType) = when (owner) {
        OwnerType.ANNOUNCEMENT -> R.string.notes_type_announcement
        OwnerType.ATTENDANCE -> R.string.notes_type_attendance
        OwnerType.BEHAVIOR -> R.string.notes_type_behavior
        OwnerType.DAY -> R.string.notes_type_day
        OwnerType.EVENT -> R.string.notes_type_event
        OwnerType.EVENT_SUBJECT -> TODO()
        OwnerType.GRADE -> R.string.notes_type_grade
        OwnerType.LESSON -> R.string.notes_type_lesson
        OwnerType.LESSON_SUBJECT -> TODO()
        OwnerType.MESSAGE -> R.string.notes_type_message
        OwnerType.NONE -> throw Exception("NONE is not a valid OwnerType.")
    }

    fun getOwnerTypeImage(owner: OwnerType) = when (owner) {
        OwnerType.ANNOUNCEMENT -> R.drawable.ic_announcement
        OwnerType.ATTENDANCE -> R.drawable.ic_attendance
        OwnerType.BEHAVIOR -> R.drawable.ic_behavior
        OwnerType.DAY -> R.drawable.ic_calendar_day
        OwnerType.EVENT -> R.drawable.ic_calendar_event
        OwnerType.EVENT_SUBJECT -> TODO()
        OwnerType.GRADE -> R.drawable.ic_grade
        OwnerType.LESSON -> R.drawable.ic_timetable
        OwnerType.LESSON_SUBJECT -> TODO()
        OwnerType.MESSAGE -> R.drawable.ic_message
        OwnerType.NONE -> throw Exception("NONE is not a valid OwnerType.")
    }

    fun configureHeader(
        activity: AppCompatActivity,
        noteOwner: Noteable?,
        b: NoteDialogHeaderBinding,
    ) {
        if (noteOwner == null) {
            b.title.isVisible = false
            b.divider.isVisible = false
            b.ownerItemList.isVisible = false
            return
        }
        b.ownerItemList.apply {
            adapter = getAdapterForItem(activity, noteOwner)
            isNestedScrollingEnabled = false
            //setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
        }

        b.title.setText(getOwnerTypeText(noteOwner.getNoteType()))
        b.title.setCompoundDrawables(
            getOwnerTypeImage(noteOwner.getNoteType()).resolveDrawable(activity),
            null,
            null,
            null,
        )
    }
}
