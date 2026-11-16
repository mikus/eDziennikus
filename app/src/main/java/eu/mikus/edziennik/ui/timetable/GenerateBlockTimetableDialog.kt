/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-5
 */

package eu.mikus.edziennik.ui.timetable

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import eu.mikus.edziennik.*
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskFinishedEvent
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.databinding.DialogGenerateBlockTimetableBinding
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.utils.PublicFolder
import eu.mikus.edziennik.utils.Utils
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.models.Week
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class GenerateBlockTimetableDialog(
        val activity: AppCompatActivity,
        val onShowListener: ((tag: String) -> Unit)? = null,
        val onDismissListener: ((tag: String) -> Unit)? = null
) : CoroutineScope {
    companion object {
        const val TAG = "GenerateBlockTimetableDialog"

        private const val WIDTH_CONSTANT = 70
        private const val WIDTH_WEEKDAY = 285
        private const val WIDTH_SPACING = 15
        private const val HEIGHT_CONSTANT = 60
        private const val HEIGHT_MINUTE = 3
        private const val HEIGHT_FOOTER = 40
    }

    private val heightProfileName by lazy { if (showProfileName) 100 else 0 }

    private val app by lazy { activity.application as App }

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private lateinit var dialog: AlertDialog
    private lateinit var b: DialogGenerateBlockTimetableBinding

    private var showProfileName: Boolean = false
    private var showTeachersNames: Boolean = true
    private var noColors: Boolean = false

    /** A sync this dialog enqueued for a week it found empty, and the dialog shown meanwhile. */
    private class Waiting(val weekStart: Date, val weekEnd: Date, val dialog: AlertDialog)

    /** What a generation does when the week turns out to have no lessons; fixed by its caller. */
    private enum class IfEmpty {
        /** The user's Save or the date picker: sync the week and wait for it. */
        START_WAIT,
        /** The rebuild after a Finished: that may not have been this dialog's sync, so keep waiting. */
        KEEP_WAITING,
        /** The read after the queue drained: say the week has no lessons, if it was downloaded. */
        SAY_NO_LESSONS,
    }

    /** Non-null exactly while this dialog is waiting for its own sync; see [startWait], [endWait]. */
    private var waiting: Waiting? = null

    init { run {
        if (activity.isFinishing)
            return@run
        onShowListener?.invoke(TAG)
        EventBus.getDefault().register(this)

        val weekCurrentStart = Week.getWeekStart()
        val weekCurrentEnd = Week.getWeekEnd()
        val weekNextStart = weekCurrentEnd.clone().stepForward(0, 0, 1)
        val weekNextEnd = weekNextStart.clone().stepForward(0, 0, 6)

        b = DialogGenerateBlockTimetableBinding.inflate(activity.layoutInflater)

        b.withChangesCurrentWeekRadio.setText(R.string.timetable_generate_current_week_format, weekCurrentStart.formattedStringShort, weekCurrentEnd.formattedStringShort)
        b.withChangesNextWeekRadio.setText(R.string.timetable_generate_next_week_format, weekNextStart.formattedStringShort, weekNextEnd.formattedStringShort)

        b.showProfileNameCheckbox.setOnCheckedChangeListener { _, isChecked -> showProfileName = isChecked }
        b.showTeachersNamesCheckbox.setOnCheckedChangeListener { _, isChecked -> showTeachersNames = isChecked }
        b.noColorsCheckbox.setOnCheckedChangeListener { _, isChecked -> noColors = isChecked }

        dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.timetable_generate_range)
                .setView(b.root)
                .setNeutralButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(R.string.save, null)
                .setOnDismissListener {
                    onDismissListener?.invoke(TAG)
                    EventBus.getDefault().unregister(this@GenerateBlockTimetableDialog)
                    // The handlers just unregistered were the only other way the wait could end.
                    // One can still be on: a read that resumed before a posted Cancel click ran saw
                    // this dialog up, so the guard in generateBlockTimetable let it through. A
                    // rebuild that finds the week also ends the wait here, as it dismisses this dialog.
                    endWait()
                }
                .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.onClick {
            when (b.weekSelectionRadioGroup.checkedRadioButtonId) {
                R.id.withChangesCurrentWeekRadio -> generateBlockTimetable(weekCurrentStart, weekCurrentEnd, IfEmpty.START_WAIT)
                R.id.withChangesNextWeekRadio -> generateBlockTimetable(weekNextStart, weekNextEnd, IfEmpty.START_WAIT)
                R.id.forSelectedWeekRadio -> selectDate()
            }
        }
    }}

    private fun selectDate() {
        MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(app.profile.getSchoolYearConstrains())
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    val dateSelected = Date.fromMillisUtc(millis)
                    generateBlockTimetable(dateSelected.weekStart, dateSelected.weekEnd, IfEmpty.START_WAIT)
                }
            }
            .show(activity.supportFragmentManager, TAG)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskFinishedEvent(event: ApiTaskFinishedEvent) {
        // Only while this dialog is waiting for a sync it enqueued: any task that finishes for the
        // profile posts this event, including the periodic and launch syncs, and with no wait there
        // is no week to rebuild.
        //
        // The wait is NOT ended here. The event carries no task identity, so this may well be some
        // other task finishing while the dialog's own sync is still queued behind it; ending the
        // wait on it re-read a week that was still empty and returned silently below, so the
        // dialog's own sync was then ignored and the image never made. The wait ends where it is
        // actually over: a rebuild that finds the week (it dismisses this dialog), the queue
        // draining (onApiTaskAllFinishedEvent), an error, or the dialog being dismissed.
        //
        // The rebuild may not start a wait of its own: its read can outlive this wait, and a new
        // sync for a week that has no lessons would only finish into another empty read. Nor may it
        // say the week has no lessons, for the same reason the wait is not ended here.
        val current = waiting ?: return
        if (event.profileId == App.profileId)
            generateBlockTimetable(current.weekStart, current.weekEnd, IfEmpty.KEEP_WAITING)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskAllFinishedEvent(event: ApiTaskAllFinishedEvent) {
        // Nothing is left to wait for, but no event says whether this dialog's own sync ran: none
        // carries a task identity, so a Finished for this profile may be the task ahead's, handled
        // only after this dialog's task has started, and a task posted into a closing service is
        // dropped unrun before a drain. So the week is read once more and the read decides (see
        // SAY_NO_LESSONS below). It is a fresh read rather than an earlier rebuild's result, which
        // may predate the sync's write; if it finds lessons, it generates the image.
        val drained = waiting ?: return
        endWait()
        generateBlockTimetable(drained.weekStart, drained.weekEnd, IfEmpty.SAY_NO_LESSONS)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskErrorEvent(event: ApiTaskErrorEvent) {
        // Only an error that can be this dialog's own sync's: with no wait, nothing this dialog
        // started can fail. Its sync is enqueued for App.profileId; an unstamped error can be its own,
        // a refused service start delivered inline from inside startWait's enqueue. Keyed on the
        // null, not on the error code, so a future unstamped error still ends the wait.
        if (waiting == null) return
        if (!event.error.mayBelongTo(App.profileId)) return
        dialog.dismiss()
        endWait()
    }

    /**
     * The only creator of a [Waiting]. The enqueue is deliberately the last statement: a refused
     * service start is reported by [EdziennikTask.enqueue] as an [ApiTaskErrorEvent] delivered
     * inline, so the wait must already be set for that error to end it, and it may be over by the
     * time `enqueue` returns.
     */
    private fun startWait(weekStart: Date, weekEnd: Date) {
        val waitDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.please_wait)
                .setMessage(R.string.timetable_syncing_text)
                .setCancelable(false)
                .show()
        val started = Waiting(weekStart, weekEnd, waitDialog)
        waiting = started

        EdziennikTask.syncProfile(
                profileId = App.profileId,
                featureTypes = setOf(FeatureType.TIMETABLE),
                arguments = JsonObject(
                        "weekStart" to started.weekStart.stringY_m_d
                )
        ).enqueue(activity)
    }

    private fun endWait() {
        waiting?.dialog?.dismiss()
        waiting = null
    }

    /**
     * [ifEmpty] is fixed by the caller before the launch, never derived from [waiting] after the
     * read: see [IfEmpty]. A week with lessons is generated whichever it is.
     */
    private fun generateBlockTimetable(weekStart: Date, weekEnd: Date, ifEmpty: IfEmpty) { launch {
        val weekDays = mutableListOf<MutableList<Lesson>>()
        for (i in weekStart.weekDay..weekEnd.weekDay) {
            weekDays.add(mutableListOf())
        }

        val allLessons = withContext(Dispatchers.Default) {
            app.db.timetableDao().getBetweenDatesNow(weekStart, weekEnd)
        }
        // Dismissed while the read ran: stop before the wait dialog, whose dismissers the dismiss
        // listener has unregistered, and before any generation the user walked away from.
        // isShowing, not a flag set in that listener: the listener is a posted message, so a
        // resume queued ahead of it would still see the flag unset. The job is deliberately not
        // cancelled on dismissal: the success path below dismisses this dialog itself.
        if (!dialog.isShowing) return@launch
        val lessonRanges = mutableMapOf<Int, Int>()

        var maxWeekDay = 5
        var minTime: Time? = null
        var maxTime: Time? = null

        val lessons: List<LessonFull> = allLessons.mapNotNull { lesson ->
            if (lesson.profileId != app.profile.id || lesson.type == Lesson.TYPE_NO_LESSONS
                    || lesson.displayDate == null || lesson.displayStartTime == null || lesson.displayEndTime == null)
                return@mapNotNull null

            if (lesson.displayDate!!.weekDay > maxWeekDay)
                maxWeekDay = lesson.displayDate!!.weekDay

            lessonRanges[lesson.displayStartTime!!.value] = lesson.displayEndTime!!.value
            weekDays[lesson.displayDate!!.weekDay].add(lesson)

            if (minTime == null || lesson.displayStartTime!! < minTime) {
                minTime = lesson.displayStartTime!!.clone()
            }

            if (maxTime == null || lesson.displayEndTime!! > maxTime) {
                maxTime = lesson.displayEndTime!!.clone()
            }

            return@mapNotNull lesson
        }

        if (lessons.isEmpty()) {
            when (ifEmpty) {
                // A second Save overlapping a wait (a double tap) keeps waiting and enqueues nothing.
                IfEmpty.START_WAIT -> if (waiting == null) startWait(weekStart, weekEnd)
                IfEmpty.KEEP_WAITING -> Unit
                // Only for a week that was downloaded: a timetable sync writes a TYPE_NO_LESSONS
                // marker for each day without lessons, so an empty week it delivered still has
                // this profile's markers, and a week no sync delivered has no rows at all. The
                // read spans every profile, hence the profile check.
                IfEmpty.SAY_NO_LESSONS ->
                    if (allLessons.any { it.profileId == app.profile.id && it.type == Lesson.TYPE_NO_LESSONS })
                        Toast.makeText(activity, R.string.timetable_generate_no_lessons, Toast.LENGTH_LONG).show()
            }
            return@launch
        }

        val progressDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.timetable_generate_progress_title)
                .setMessage(R.string.timetable_generate_progress_text)
                .show()

        if (minTime == null) {
            progressDialog.dismiss()
            // TODO: Toast
            return@launch
        }

        dialog.dismiss()

        val uri = withContext(Dispatchers.Default) { nullIfDrawingFails {

            val diff = Time.diff(maxTime, minTime)

            val imageWidth = WIDTH_CONSTANT + maxWeekDay * (WIDTH_WEEKDAY + WIDTH_SPACING) - WIDTH_SPACING
            val imageHeight = heightProfileName + HEIGHT_CONSTANT + diff.inMinutes * HEIGHT_MINUTE + HEIGHT_FOOTER
            val bitmap = Bitmap.createBitmap(imageWidth + 20, imageHeight + 30, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (noColors) canvas.drawARGB(255, 255, 255, 255)
            else canvas.drawARGB(255, 225, 225, 225)

            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }

            lessons.forEach { lesson ->
                val lessonLength = Time.diff(lesson.displayEndTime, lesson.displayStartTime)
                val firstOffset = Time.diff(lesson.displayStartTime, minTime)
                val lessonWeekDay = lesson.displayDate!!.weekDay

                val left = WIDTH_CONSTANT + lessonWeekDay * (WIDTH_WEEKDAY + WIDTH_SPACING)
                val top = heightProfileName + HEIGHT_CONSTANT + firstOffset.inMinutes * HEIGHT_MINUTE

                val blockWidth = WIDTH_WEEKDAY
                val blockHeight = lessonLength.inMinutes * HEIGHT_MINUTE

                val viewWidth = 380.dp
                val viewHeight = lessonLength.inMinutes * 4.dp

                val layout = activity.layoutInflater.inflate(R.layout.row_timetable_block_item, null) as LinearLayout

                val item: LinearLayout = layout.findViewById(R.id.timetableItemLayout)
                val card: CardView = layout.findViewById(R.id.timetableItemCard)
                val subjectName: TextView = layout.findViewById(R.id.timetableItemSubjectName)
                val classroomName: TextView = layout.findViewById(R.id.timetableItemClassroomName)
                val teacherName: TextView = layout.findViewById(R.id.timetableItemTeacherName)
                val teamName: TextView = layout.findViewById(R.id.timetableItemTeamName)

                if (noColors) {
                    card.setCardBackgroundColor(Color.WHITE)
                    card.cardElevation = 0f
                    item.setBackgroundResource(R.drawable.bg_rounded_16dp_outline)
                    subjectName.setTextColor(Color.BLACK)
                    classroomName.setTextColor(0xffaaaaaa.toInt())
                    teacherName.setTextColor(0xffaaaaaa.toInt())
                    teamName.setTextColor(0xffaaaaaa.toInt())
                }

                subjectName.text = lesson.displaySubjectName ?: ""
                classroomName.text = lesson.displayClassroom ?: ""
                teacherName.text = lesson.displayTeacherName ?: ""
                teamName.text = lesson.displayTeamName ?: ""

                if (!showTeachersNames) teacherName.visibility = View.GONE

                when (lesson.type) {
                    Lesson.TYPE_NORMAL -> {
                    }
                    Lesson.TYPE_CANCELLED, Lesson.TYPE_SHIFTED_SOURCE -> {
                        card.setCardBackgroundColor(Color.BLACK)
                        subjectName.setTextColor(Color.WHITE)
                        subjectName.text = lesson.displaySubjectName?.asStrikethroughSpannable()
                                ?: ""
                    }
                    else -> {
                        card.setCardBackgroundColor(0xff234158.toInt())
                        subjectName.setTextColor(Color.WHITE)
                        subjectName.setTypeface(null, Typeface.BOLD_ITALIC)
                    }
                }

                layout.isDrawingCacheEnabled = true
                layout.measure(MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY))
                layout.layout(0, 0, layout.measuredWidth, layout.measuredHeight)
                layout.buildDrawingCache(true)

                val itemBitmap = layout.drawingCache
                canvas.drawBitmap(itemBitmap, null, Rect(left, top, left + blockWidth, top + blockHeight), paint)
            }

            val textPaint = Paint().apply {
                setARGB(255, 0, 0, 0)
                textAlign = Paint.Align.CENTER
                textSize = 30f
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }

            for (w in 0..maxWeekDay) {
                val x = WIDTH_CONSTANT + w * WIDTH_WEEKDAY + w * WIDTH_SPACING
                canvas.drawText(Week.getFullDayName(w), x + (WIDTH_WEEKDAY / 2f), heightProfileName + HEIGHT_CONSTANT / 2 + 10f, textPaint)
            }

            if (showProfileName) {
                textPaint.textSize = 50f
                canvas.drawText("${app.profile.name} - plan lekcji, ${weekStart.formattedStringShort} - ${weekEnd.formattedStringShort}", (imageWidth + 20) / 2f, 80f, textPaint)
            }

            textPaint.apply {
                setARGB(128, 0, 0, 0)
                textAlign = Paint.Align.RIGHT
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }

            val footerTextPaintCenter = ((textPaint.descent() + textPaint.ascent()) / 2).roundToInt()
            canvas.drawText("Wygenerowano w aplikacji eDziennikus", imageWidth - 10f, imageHeight - footerTextPaintCenter - 10f, textPaint)

            textPaint.apply {
                setARGB(255, 127, 127, 127)
                textAlign = Paint.Align.CENTER
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val textPaintCenter = ((textPaint.descent() + textPaint.ascent()) / 2).roundToInt()

            val linePaint = Paint().apply {
                setARGB(255, 100, 100, 100)
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }

            val minTimeInt = ((minTime.value / 10000) * 60) + ((minTime.value / 100) % 100)

            lessonRanges.forEach { (startTime, endTime) ->
                listOf(startTime, endTime).forEach { value ->
                    val hour = value / 10000
                    val minute = (value / 100) % 100
                    val time = Time(hour, minute, 0)

                    val firstOffset = time.inMinutes - minTimeInt // offset in minutes
                    val top = (heightProfileName + HEIGHT_CONSTANT + firstOffset * HEIGHT_MINUTE).toFloat()

                    canvas.drawText(time.stringHM, WIDTH_CONSTANT / 2f, top - textPaintCenter, textPaint)
                    canvas.drawLine(WIDTH_CONSTANT.toFloat(), top, imageWidth.toFloat(), top, linePaint)
                }
            }

            val today = Date.getToday().stringY_m_d
            val now = Time.getNow().stringH_M_S
            val filename = "plan_lekcji_${app.profile.name}_${today}_${now}.png"
            savePng(bitmap, filename)
        }} ?: run {
            progressDialog.dismiss()
            Toast.makeText(activity, R.string.timetable_generate_save_failed, Toast.LENGTH_LONG).show()
            return@launch
        }

        progressDialog.dismiss()
        MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.timetable_generate_success_title)
                .setMessage(R.string.timetable_generate_success_text)
                .setPositiveButton(R.string.share) { dialog, _ ->
                    dialog.dismiss()

                    val intent = Intent(Intent.ACTION_SEND)
                    intent.setDataAndType(null, "image/*")
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_intent)))
                }
                .setNegativeButton(R.string.open) { dialog, _ ->
                    dialog.dismiss()

                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "image/*")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    activity.startActivity(intent)
                }
                .setNeutralButton(R.string.do_nothing) { dialog, _ -> dialog.dismiss() }
                .show()
    }}

    /**
     * Runs [draw], the drawing and the save, and turns a failure into null, which the caller reports
     * as "could not save". [OutOfMemoryError] is caught as well as [Exception]: the image's bitmap
     * is the realistic failure, and it is this block's own allocation, unreachable once it unwinds,
     * so carrying on is normally safe. The cost is that a heap exhausted for another reason fails
     * later elsewhere, and the evidence is that later crash: [Utils.d] writes only in dev mode, so a
     * release build keeps no trace of this one. Every other [Error] means a broken build or VM, not a
     * big image, and still crashes. No CancellationException can arise: [draw] has no
     * suspension point, and the job is never cancelled.
     */
    private inline fun nullIfDrawingFails(draw: () -> Uri?): Uri? = try {
        draw()
    } catch (e: Exception) {
        Utils.d(TAG, "Generating the timetable image failed: ${e.stackTraceToString()}")
        null
    } catch (e: OutOfMemoryError) {
        Utils.d(TAG, "Generating the timetable image ran out of memory: ${e.stackTraceToString()}")
        null
    }

    /**
     * Saves [bitmap] as a PNG to MediaStore and returns its uri, or null unless the row was inserted,
     * a stream opened, the image written and the stream closed. Any other outcome after the insert
     * deletes the row, so no empty image is left behind. `compress` reports a failing stream by
     * returning false, not by throwing.
     */
    private fun savePng(bitmap: Bitmap, filename: String): Uri? {
        val resolver: ContentResolver = activity.applicationContext.contentResolver
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, PublicFolder.relativePath(Environment.DIRECTORY_PICTURES))
        } else {
            val picturesDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), PublicFolder.NAME)
            picturesDirectory.mkdirs()
            values.put(MediaStore.MediaColumns.DATA, File(picturesDirectory, filename).path)
        }

        var uri: Uri? = null
        var written = false
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Utils.d(TAG, "Saving $filename failed: the MediaStore insert returned null")
            } else {
                val stream = resolver.openOutputStream(uri)
                if (stream == null) {
                    Utils.d(TAG, "Saving $filename failed: no output stream for $uri")
                } else {
                    written = stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (!written) Utils.d(TAG, "Saving $filename failed: compress returned false")
                }
            }
        } catch (e: Exception) {
            Utils.d(TAG, "Saving $filename failed: ${e.stackTraceToString()}")
        } finally {
            // The one cleanup point: every exit after a successful insert without a written image.
            if (!written && uri != null) {
                try {
                    resolver.delete(uri, null, null)
                } catch (e: Exception) {
                    Utils.d(TAG, "Could not delete the unwritten image $uri: $e")
                }
            }
        }
        return if (written) uri else null
    }
}
