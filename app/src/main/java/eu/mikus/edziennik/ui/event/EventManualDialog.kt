/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.event

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.config.AppData
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskFinishedEvent
import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.EventType
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.entity.Metadata
import eu.mikus.edziennik.data.db.entity.Subject
import eu.mikus.edziennik.data.db.entity.Teacher
import eu.mikus.edziennik.data.db.entity.Team
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ext.JsonObject
import eu.mikus.edziennik.ext.getStudentData
import eu.mikus.edziennik.ext.observeOnce
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.base.FormDropdown
import eu.mikus.edziennik.ui.dialogs.base.FormDropdownItem
import eu.mikus.edziennik.ui.dialogs.base.RichTextFieldBridge
import eu.mikus.edziennik.utils.html.BetterHtml
import eu.mikus.edziennik.utils.managers.TextStylingManager
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.models.Week
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class EventManualDialog(
    activity: AppCompatActivity,
    private val profileId: Int,
    private val defaultLesson: LessonFull? = null,
    private val defaultDate: Date? = null,
    private val defaultTime: Time? = null,
    private val defaultType: Long? = null,
    private val editingEvent: EventFull? = null,
    private val onSaveListener: ((event: EventFull?) -> Unit)? = null,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "EventManualDialog"

    override fun getTitleRes() = R.string.dialog_event_manual_title
    override fun isCancelable() = false
    override fun getPositiveButtonText() = R.string.save
    override fun getNeutralButtonText() = if (editingEvent != null) R.string.remove else null
    override fun getNegativeButtonText() = R.string.cancel

    // ---- Loaded once in loadInitial() -------------------------------------------------------
    // Rich-text bridge stashes its config the first time the field's factory runs; getHtmlText()
    // reads back from it at save time (identical to the legacy stylingConfig usage).
    private lateinit var topicConfig: StylingConfigBase
    private var classTeamId: Long? = null
    private var typeColorById: Map<Long, Int> = emptyMap()
    private val nextLessonTeamId: Long? = defaultLesson?.displayTeamId

    // ---- Reactive dropdown item lists (hoisted to the class so the suspend button handlers can
    //      read the current tags at save time, exactly like the legacy getSelected() calls) -----
    private val dateItems = mutableStateOf<List<FormDropdownItem>>(emptyList())
    private val timeItems = mutableStateOf<List<FormDropdownItem>>(emptyList())
    private val teamItems = mutableStateOf<List<FormDropdownItem>>(emptyList())
    private val subjectItems = mutableStateOf<List<FormDropdownItem>>(emptyList())
    private val teacherItems = mutableStateOf<List<FormDropdownItem>>(emptyList())
    private val typeItems = mutableStateOf<List<FormDropdownItem>>(emptyList())

    // ---- Selection state --------------------------------------------------------------------
    private val selectedDate = mutableStateOf<Date?>(null)
    private val selectedDateId = mutableStateOf<Long?>(null)
    // 0L = all-day; a lesson-start value = a lesson item; a Time value = a custom time. Resolved to
    // a TimeSelection on demand via resolveTimeSelection().
    private val selectedTimeId = mutableStateOf<Long?>(null)
    private val selectedTeamId = mutableStateOf<Long?>(null)
    private val selectedSubjectId = mutableStateOf<Long?>(null)
    private val selectedTeacherId = mutableStateOf<Long?>(null)
    private val selectedTypeId = mutableStateOf<Long?>(null)
    private val customColor = mutableStateOf<Int?>(null)

    private val showSubjectInMain = mutableStateOf(false)
    private val moreExpanded = mutableStateOf(false)
    private val validation = mutableStateOf<ValidationResult?>(null)

    // ---- Timetable-sync wait dialog (driven by the EventBus handlers) ------------------------
    private var enqueuedWeekDialog: AlertDialog? = null

    // =========================================================================================
    // Lifecycle
    // =========================================================================================

    // Register on EventBus in onShow() (NOT onBeforeShow — that override is final in ComposeDialog).
    override suspend fun onShow() {
        EventBus.getDefault().register(this@EventManualDialog)
    }

    override fun onDismiss() {
        EventBus.getDefault().unregister(this@EventManualDialog)
        enqueuedWeekDialog?.dismiss()
    }

    @Composable
    override fun Content() {
        val app = activity.applicationContext as App
        LaunchedEffect(Unit) { loadInitial(app) }

        val v = validation.value
        val initialTopicHtml = remember {
            editingEvent?.topic?.let { BetterHtml.fromHtml(activity, it, nl2br = true) }
        }
        val swatch = swatchColorArgb()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormDropdown(
                hint = stringResource(R.string.dialog_event_manual_date),
                items = dateItems.value,
                selectedId = selectedDateId.value,
                onSelect = { onDateSelect(it) },
                isError = v?.dateInvalid == true,
                supportingText = if (v?.dateInvalid == true)
                    stringResource(R.string.dialog_event_manual_date_choose) else null,
            )

            FormDropdown(
                hint = stringResource(R.string.dialog_event_manual_time),
                items = timeItems.value,
                selectedId = selectedTimeId.value,
                onSelect = { onTimeSelect(it) },
                isError = v?.timeInvalid == true,
                supportingText = if (v?.timeInvalid == true)
                    stringResource(R.string.dialog_event_manual_time_choose) else null,
            )

            FormDropdown(
                hint = stringResource(R.string.dialog_event_manual_team),
                items = teamItems.value,
                selectedId = selectedTeamId.value,
                onSelect = { onTeamSelect(it) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                FormDropdown(
                    hint = stringResource(R.string.dialog_event_manual_type),
                    items = typeItems.value,
                    selectedId = selectedTypeId.value,
                    onSelect = { onTypeSelect(it) },
                    modifier = Modifier.weight(1f),
                    isError = v?.typeInvalid == true,
                    supportingText = if (v?.typeInvalid == true)
                        stringResource(R.string.dialog_event_manual_type_choose) else null,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .clickable { openColorPicker() },
                )
            }

            RichTextFieldBridge(
                app = app,
                activity = activity,
                hint = stringResource(R.string.dialog_event_manual_topic),
                initialHtml = initialTopicHtml,
                htmlMode = TextStylingManager.HtmlMode.SIMPLE,
                onConfigReady = { topicConfig = it },
                modifier = Modifier.fillMaxWidth(),
                error = if (v?.topicInvalid == true)
                    stringResource(R.string.dialog_event_manual_topic_choose) else null,
                minLines = 2,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            )

            // When the login-type UI config re-parents the subject dropdown into the main area, it
            // shows here (always visible) instead of inside the collapsible "more options" section.
            if (showSubjectInMain.value) {
                FormDropdown(
                    hint = stringResource(R.string.dialog_event_manual_subject),
                    items = subjectItems.value,
                    selectedId = selectedSubjectId.value,
                    onSelect = { onSubjectSelect(it) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { moreExpanded.value = !moreExpanded.value }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.dialog_event_manual_more_options),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconicsIcon(
                    if (moreExpanded.value) CommunityMaterial.Icon.cmd_chevron_up
                    else CommunityMaterial.Icon.cmd_chevron_down,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = moreExpanded.value) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!showSubjectInMain.value) {
                        FormDropdown(
                            hint = stringResource(R.string.dialog_event_manual_subject),
                            items = subjectItems.value,
                            selectedId = selectedSubjectId.value,
                            onSelect = { onSubjectSelect(it) },
                        )
                    }
                    FormDropdown(
                        hint = stringResource(R.string.dialog_event_manual_teacher),
                        items = teacherItems.value,
                        selectedId = selectedTeacherId.value,
                        onSelect = { onTeacherSelect(it) },
                    )
                }
            }
        }
    }

    // =========================================================================================
    // Initial load + defaults (mirrors the legacy loadLists() ordering exactly)
    // =========================================================================================

    private suspend fun loadInitial(app: App) {
        val loaded = withContext(Dispatchers.IO) { app.db.profileDao().getByIdNow(profileId) }
        if (loaded == null) {
            Toast.makeText(activity, R.string.event_manual_no_profile, Toast.LENGTH_SHORT).show()
            return
        }
        showSubjectInMain.value = withContext(Dispatchers.IO) {
            AppData.get(loaded.loginStoreType).uiConfig.eventManualShowSubjectDropdown
        }

        buildTypeItems(app)
        buildTeamItems(app)
        buildSubjectItems(app)
        buildTeacherItems(app)
        buildDateItems(app)

        // Date default (no cascade — programmatic selects never fired onChange in the legacy views).
        selectDefaultDate(editingEvent?.date)
        selectDefaultDate(defaultLesson?.displayDate ?: defaultDate)

        // Time items belong to the chosen date; build then apply time defaults.
        val initialDate = selectedDate.value ?: Date.getToday()
        if (!reloadTimeItems(app, initialDate))
            syncTimetable(app, initialDate)
        selectDefaultTime(editingEvent?.time)
        if (editingEvent != null && editingEvent.time == null)
            selectedTimeId.value = 0L // all-day
        selectDefaultTime(defaultLesson?.displayStartTime ?: defaultTime)

        // Team: default to the class team, then the (no-op unless still unset) defaults, then the
        // final forced override from defaultLesson — faithfully reproducing the legacy sequence.
        selectedTeamId.value = classTeamId
        selectDefaultTeam(editingEvent?.teamId)
        selectDefaultTeam(defaultLesson?.displayTeamId)

        selectDefaultSubject(editingEvent?.subjectId)
        selectDefaultSubject(defaultLesson?.displaySubjectId)

        selectDefaultTeacher(editingEvent?.teacherId)
        selectDefaultTeacher(defaultLesson?.displayTeacherId)

        selectDefaultType(editingEvent?.type)
        selectDefaultType(defaultType)

        customColor.value = EventManualLogic.seedCustomColor(editingEvent?.color)

        defaultLesson?.let { selectedTeamId.value = it.displayTeamId }
    }

    private suspend fun buildTypeItems(app: App) {
        val types = withContext(Dispatchers.Default) {
            app.db.eventTypeDao().getAllNow(profileId).sortedBy { it.order }
        }
        typeColorById = types.associate { it.id to it.color }
        typeItems.value = types.map {
            FormDropdownItem(id = it.id, text = it.name, leadingColorInt = it.color, tag = it)
        }
    }

    private suspend fun buildTeamItems(app: App) {
        val teams = withContext(Dispatchers.Default) { app.db.teamDao().getAllNow(profileId) }
        classTeamId = teams.singleOrNull { it.type == Team.TYPE_CLASS }?.id
        teamItems.value = buildList {
            add(FormDropdownItem(id = -1L, text = app.getString(R.string.dialog_event_manual_no_team)))
            addAll(teams.map { FormDropdownItem(id = it.id, text = it.name, tag = it) })
        }
    }

    private suspend fun buildSubjectItems(app: App) {
        val subjects = withContext(Dispatchers.Default) { app.db.subjectDao().getAllNow(profileId) }
        subjectItems.value = buildList {
            add(FormDropdownItem(id = -1L, text = app.getString(R.string.dialog_event_manual_no_subject)))
            addAll(subjects.map { FormDropdownItem(id = it.id, text = it.longName, tag = it) })
        }
    }

    private suspend fun buildTeacherItems(app: App) {
        val teachers = withContext(Dispatchers.Default) { app.db.teacherDao().getAllNow(profileId) }
        teacherItems.value = buildList {
            add(FormDropdownItem(id = -1L, text = app.getString(R.string.dialog_event_manual_no_teacher)))
            addAll(teachers.map { FormDropdownItem(id = it.id, text = it.fullName, tag = it) })
        }
    }

    private fun buildDateItems(app: App) {
        val choices = EventManualLogic.buildDateChoices(
            today = Date.getToday(),
            nextLessonSubjectId = defaultLesson?.displaySubjectId,
            nextLessonSubjectName = defaultLesson?.displaySubjectName,
        )
        dateItems.value = choices.map { choice ->
            when (choice.kind) {
                DateChoiceKind.NEXT_LESSON -> FormDropdownItem(
                    id = -(choice.subjectId ?: 0L),
                    text = app.getString(R.string.dialog_event_manual_date_next_lesson, choice.subjectName),
                    tag = choice,
                )
                DateChoiceKind.TODAY -> FormDropdownItem(
                    id = choice.date!!.value.toLong(),
                    text = app.getString(R.string.dialog_event_manual_date_today, choice.date.formattedString),
                    tag = choice.date,
                )
                DateChoiceKind.TOMORROW -> FormDropdownItem(
                    id = choice.date!!.value.toLong(),
                    text = app.getString(R.string.dialog_event_manual_date_tomorrow, choice.date.formattedString),
                    tag = choice.date,
                )
                DateChoiceKind.THIS_WEEK -> FormDropdownItem(
                    id = choice.date!!.value.toLong(),
                    text = app.getString(
                        R.string.dialog_event_manual_date_this_week,
                        Week.getFullDayName(choice.weekDay!!),
                        choice.date.formattedString,
                    ),
                    tag = choice.date,
                )
                DateChoiceKind.NEXT_WEEK -> FormDropdownItem(
                    id = choice.date!!.value.toLong(),
                    text = app.getString(
                        R.string.dialog_event_manual_date_next_week,
                        Week.getFullDayName(choice.weekDay!!),
                        choice.date.formattedString,
                    ),
                    tag = choice.date,
                )
                DateChoiceKind.OTHER -> FormDropdownItem(
                    id = -1L,
                    text = app.getString(R.string.dialog_event_manual_date_other),
                    tag = choice,
                )
            }
        }
    }

    /** Off-IO build of the time list for [date]. Returns `false` when there is no timetable (so the
     *  caller enqueues a sync), mirroring TimeDropdown.loadItems(). */
    private suspend fun buildTimeItems(app: App, date: Date): Pair<List<FormDropdownItem>, Boolean> =
        withContext(Dispatchers.Default) {
            val list = mutableListOf<FormDropdownItem>()
            list += FormDropdownItem(
                id = 0L,
                text = app.getString(R.string.dialog_event_manual_all_day),
                tag = TimeSelection.AllDay,
            )
            list += FormDropdownItem(
                id = -1L,
                text = app.getString(R.string.dialog_event_manual_custom_time),
            )

            val lessons = app.db.timetableDao().getAllForDateNow(profileId, date)
            if (lessons.isEmpty()) {
                list += FormDropdownItem(-2L, app.getString(R.string.dialog_event_manual_no_timetable))
                return@withContext list to false
            }

            lessons.forEach { lesson ->
                if (lesson.type == Lesson.TYPE_NO_LESSONS) {
                    list += FormDropdownItem(-2L, app.getString(R.string.dialog_event_manual_no_lessons))
                    return@forEach
                }
                // Plain-text label: FormDropdownItem.text is a String, so the legacy strikethrough/
                // italic spannables for cancelled/shifted lessons collapse to plain text here.
                val name = lesson.displaySubjectName
                val label = buildString {
                    append(lesson.displayStartTime?.stringHM ?: "")
                    if (name != null) {
                        append(" - ")
                        append(name)
                    }
                }
                list += FormDropdownItem(
                    id = lesson.displayStartTime?.value?.toLong() ?: -1L,
                    text = label,
                    tag = lesson,
                )
            }
            list to true
        }

    private suspend fun reloadTimeItems(app: App, date: Date): Boolean {
        val (items, hasTimetable) = buildTimeItems(app, date)
        timeItems.value = items
        return hasTimetable
    }

    // =========================================================================================
    // Programmatic (no-cascade) selection helpers — the "selectDefault" family
    // =========================================================================================

    private fun setDateSelection(date: Date) {
        selectedDate.value = date
        selectedDateId.value = date.value.toLong()
        if (dateItems.value.none { it.id == date.value.toLong() }) {
            dateItems.value = dateItems.value +
                FormDropdownItem(id = date.value.toLong(), text = date.formattedString, tag = date)
        }
    }

    private fun selectDefaultDate(date: Date?) {
        if (date == null || selectedDate.value != null) return
        setDateSelection(date)
    }

    private fun selectTimeValue(time: Time) {
        val id = time.value.toLong()
        if (timeItems.value.none { it.id == id }) {
            timeItems.value = timeItems.value +
                FormDropdownItem(id = id, text = time.stringHM, tag = TimeSelection.Custom(time))
        }
        selectedTimeId.value = id
    }

    private fun selectDefaultTime(time: Time?) {
        if (time == null || selectedTimeId.value != null) return
        selectTimeValue(time)
    }

    private fun selectDefaultTeam(teamId: Long?) {
        // Faithful to TeamDropdown.selectDefault: never overrides an existing selection.
        if (teamId == null || selectedTeamId.value != null) return
        selectedTeamId.value = if (teamId == -1L) null else teamId
    }

    private fun selectDefaultSubject(subjectId: Long?) {
        if (subjectId == null || selectedSubjectId.value != null) return
        selectedSubjectId.value = if (subjectId == -1L) null else subjectId
    }

    private fun selectDefaultTeacher(teacherId: Long?) {
        if (teacherId == null || selectedTeacherId.value != null) return
        selectedTeacherId.value = if (teacherId == -1L) null else teacherId
    }

    private fun selectDefaultType(typeId: Long?) {
        if (typeId == null || selectedTypeId.value != null) return
        selectedTypeId.value = typeId
    }

    // =========================================================================================
    // User-driven selection + cascade
    // =========================================================================================

    private fun onDateSelect(item: FormDropdownItem) {
        clearValidation()
        when (val tag = item.tag) {
            is Date -> selectConcreteDate(tag, lesson = null)
            is DateChoice -> when (tag.kind) {
                DateChoiceKind.OTHER -> openDatePicker()
                DateChoiceKind.NEXT_LESSON -> resolveNextLesson(tag.subjectId ?: return)
                else -> tag.date?.let { selectConcreteDate(it, lesson = null) }
            }
        }
    }

    /** Select a concrete date, then run the legacy date cascade: reload the time list (sync if the
     *  timetable is missing) and auto-fill subject/teacher/team from [lesson] (all-cleared if null). */
    private fun selectConcreteDate(date: Date, lesson: LessonFull?) {
        val app = activity.applicationContext as App
        setDateSelection(date)
        selectedTimeId.value = null
        launch {
            if (!reloadTimeItems(app, date))
                syncTimetable(app, date)
            lesson?.displayStartTime?.let { selectTimeValue(it) }
            applyCascade(EventManualLogic.cascadeFor(lesson))
        }
    }

    private fun onTimeSelect(item: FormDropdownItem) {
        clearValidation()
        // Dispatch on the tag FIRST (mirroring the legacy TimeDropdown, which branched on it.tag):
        // a lesson row whose displayStartTime is null carries id == -1L, which would otherwise
        // collide with the custom-time trigger's -1L and wrongly open the time picker.
        when (val tag = item.tag) {
            is LessonFull -> {
                val sel = EventManualLogic.lessonToTimeSelection(tag)
                selectedTimeId.value = if (sel is TimeSelection.None) null else item.id
                applyCascade(EventManualLogic.cascadeFor(tag))
            }
            is TimeSelection.AllDay -> selectedTimeId.value = 0L // all-day
            else -> when (item.id) {
                -1L -> openTimePicker()             // custom-time picker; keep prior selection until picked
                -2L -> selectedTimeId.value = null  // "no timetable"/"no lessons" — not selectable
                else -> selectedTimeId.value = item.id // a manually-picked custom time
            }
        }
    }

    private fun onTeamSelect(item: FormDropdownItem) {
        selectedTeamId.value = if (item.id == -1L) null else item.id
    }

    private fun onSubjectSelect(item: FormDropdownItem) {
        selectedSubjectId.value = if (item.id == -1L) null else item.id
    }

    private fun onTeacherSelect(item: FormDropdownItem) {
        selectedTeacherId.value = if (item.id == -1L) null else item.id
    }

    private fun onTypeSelect(item: FormDropdownItem) {
        clearValidation()
        selectedTypeId.value = item.id
        customColor.value = EventManualLogic.customColorAfterTypeChange() // reset → retint from type
    }

    private fun applyCascade(cascade: CascadeSelection) {
        selectedSubjectId.value =
            cascade.subjectId?.takeIf { id -> subjectItems.value.any { it.id == id } }
        selectedTeacherId.value =
            cascade.teacherId?.takeIf { id -> teacherItems.value.any { it.id == id } }
        selectedTeamId.value =
            cascade.teamId?.takeIf { id -> teamItems.value.any { it.id == id } } ?: classTeamId
    }

    // =========================================================================================
    // Pickers
    // =========================================================================================

    private fun openDatePicker() {
        val date = selectedDate.value ?: Date.getToday()
        MaterialDatePicker.Builder.datePicker()
            .setSelection(date.inMillisUtc)
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    selectConcreteDate(Date.fromMillisUtc(millis), lesson = null)
                }
            }
            .show(activity.supportFragmentManager, "EventManualDate")
    }

    private fun openTimePicker() {
        val time = (resolveTimeSelection(selectedTimeId.value) as? TimeSelection.Custom)?.start
            ?: Time.getNow()
        MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(time.hour)
            .setMinute(time.minute)
            .build()
            .also { picker ->
                picker.addOnPositiveButtonClickListener {
                    clearValidation()
                    selectTimeValue(Time(picker.hour, picker.minute, 0))
                }
            }
            .show(activity.supportFragmentManager, "EventManualTime")
    }

    private fun openColorPicker() {
        val current = swatchColorArgb()
        ColorPickerDialog.newBuilder()
            .setColor(current)
            .create()
            .apply {
                setColorPickerDialogListener(object : ColorPickerDialogListener {
                    override fun onDialogDismissed(dialogId: Int) {}
                    override fun onColorSelected(dialogId: Int, color: Int) {
                        customColor.value = color
                    }
                })
            }
            .show(activity.supportFragmentManager, "color-picker-dialog")
    }

    private fun resolveNextLesson(subjectId: Long) {
        val app = activity.applicationContext as App
        val startDate = selectedDate.value ?: Date.getToday()
        val source = if (nextLessonTeamId == null)
            app.db.timetableDao().getNextWithSubject(profileId, startDate, subjectId)
        else
            app.db.timetableDao().getNextWithSubjectAndTeam(profileId, startDate, subjectId, nextLessonTeamId)
        source.observeOnce(activity, Observer { lesson ->
            if (lesson == null) {
                Toast.makeText(activity, R.string.dropdown_date_no_more_lessons, Toast.LENGTH_LONG).show()
                return@Observer
            }
            val lessonDate = lesson.displayDate ?: return@Observer
            selectConcreteDate(lessonDate, lesson)
        })
    }

    // =========================================================================================
    // Timetable sync (wait dialog + EventBus)
    // =========================================================================================

    private fun syncTimetable(app: App, date: Date) {
        if (enqueuedWeekDialog != null) return
        if (app.profile.getStudentData("timetableNotPublic", false)) return

        enqueuedWeekDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.please_wait)
            .setMessage(R.string.timetable_syncing_text)
            .setCancelable(false)
            .show()

        EdziennikTask.syncProfile(
            profileId = profileId,
            featureTypes = setOf(FeatureType.TIMETABLE),
            arguments = JsonObject("weekStart" to date.weekStart.stringY_m_d),
        ).enqueue(activity)
    }

    private fun dismissWaitDialogs() {
        enqueuedWeekDialog?.dismiss()
        enqueuedWeekDialog = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskFinishedEvent(event: ApiTaskFinishedEvent) {
        if (event.profileId != profileId) return
        dismissWaitDialogs()
        val app = activity.applicationContext as App
        launch {
            val date = selectedDate.value ?: Date.getToday()
            reloadTimeItems(app, date)
            (editingEvent?.time ?: defaultLesson?.displayStartTime ?: defaultTime)?.let {
                selectTimeValue(it)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskAllFinishedEvent(event: ApiTaskAllFinishedEvent) {
        dismissWaitDialogs()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskErrorEvent(event: ApiTaskErrorEvent) {
        dismissWaitDialogs()
    }

    // =========================================================================================
    // Save / remove
    // =========================================================================================

    override suspend fun onPositiveClick(): Boolean {
        val date = selectedDate.value
        val time = resolveTimeSelection(selectedTimeId.value)
        val typeId = selectedTypeId.value
        val topicText =
            if (::topicConfig.isInitialized) topicConfig.editText.text?.toString() else null

        val result = EventManualLogic.validate(date, time, typeId, topicText)
        validation.value = result
        if (!result.isValid) return NO_DISMISS

        val type = typeItems.value.firstOrNull { it.id == typeId }?.tag as? EventType
        val subject = subjectItems.value.firstOrNull { it.id == selectedSubjectId.value }?.tag as? Subject
        val teacher = teacherItems.value.firstOrNull { it.id == selectedTeacherId.value }?.tag as? Teacher
        val team = teamItems.value.firstOrNull { it.id == selectedTeamId.value }?.tag as? Team

        val topicHtml = app.textStylingManager.getHtmlText(topicConfig)

        val event = Event(
            profileId = profileId,
            id = editingEvent?.id ?: System.currentTimeMillis(),
            date = date!!,
            time = time.toEventTime(),
            topic = topicHtml,
            color = customColor.value,
            type = type?.id ?: Event.TYPE_DEFAULT,
            teacherId = teacher?.id ?: -1,
            subjectId = subject?.id ?: -1,
            teamId = team?.id ?: -1,
            addedDate = editingEvent?.addedDate ?: System.currentTimeMillis(),
        ).also { it.addedManually = true }

        val metadata = Metadata(
            profileId,
            if (type?.id == Event.TYPE_HOMEWORK) MetadataType.HOMEWORK else MetadataType.EVENT,
            event.id,
            true,
            true,
        )

        withContext(Dispatchers.Default) {
            app.db.eventDao().upsert(event)
            app.db.metadataDao().add(metadata)
        }

        onSaveListener?.invoke(event.withMetadata(metadata).also {
            it.subjectLongName = subject?.longName
            it.teacherName = teacher?.fullName
            it.teamName = team?.name
            it.typeName = type?.name
        })
        Toast.makeText(activity, R.string.saved, Toast.LENGTH_SHORT).show()
        return true // dismiss
    }

    override suspend fun onNeutralClick(): Boolean {
        val editing = editingEvent ?: return NO_DISMISS

        val confirmed = suspendCoroutine<Boolean> { cont ->
            var result = false
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.dialog_register_event_manual_remove_confirmation)
                .setPositiveButton(R.string.yes) { _, _ -> result = true }
                .setNegativeButton(R.string.no, null)
                .setOnDismissListener { cont.resume(result) }
                .show()
        }
        if (!confirmed) return NO_DISMISS

        withContext(Dispatchers.Default) { app.db.eventDao().remove(editing) }
        onSaveListener?.invoke(null)
        Toast.makeText(activity, R.string.removed, Toast.LENGTH_SHORT).show()
        return true // dismiss
    }

    // =========================================================================================
    // Derivations
    // =========================================================================================

    private fun resolveTimeSelection(id: Long?): TimeSelection {
        id ?: return TimeSelection.None
        if (id == 0L) return TimeSelection.AllDay
        val item = timeItems.value.firstOrNull { it.id == id } ?: return TimeSelection.None
        return when (val tag = item.tag) {
            is LessonFull -> EventManualLogic.lessonToTimeSelection(tag)
            is TimeSelection -> tag
            else -> TimeSelection.None
        }
    }

    private fun swatchColorArgb(): Int =
        EventManualLogic.swatchColor(customColor.value, typeColorById[selectedTypeId.value])

    private fun clearValidation() {
        validation.value = null
    }
}
