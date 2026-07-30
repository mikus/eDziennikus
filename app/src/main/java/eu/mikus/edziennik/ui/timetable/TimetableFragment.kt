/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.enums.MetadataType
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.databinding.TimetableFragmentBinding
import eu.mikus.edziennik.ext.JsonObject
import eu.mikus.edziennik.ext.getSchoolYearConstrains
import eu.mikus.edziennik.ext.getStudentData
import eu.mikus.edziennik.ui.base.syncFeature
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.TimetableConfigDialog
import eu.mikus.edziennik.ui.event.EventManualDialog
import eu.mikus.edziennik.utils.managers.AttendanceManager
import eu.mikus.edziennik.utils.models.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import eu.szkolny.font.SzkolnyFont
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem

class TimetableFragment : Fragment() {
    companion object {
        private const val TAG = "TimetableFragment"
        const val ACTION_SCROLL_TO_DATE = "eu.mikus.edziennik.timetable.SCROLL_TO_DATE"
        const val ACTION_RELOAD_PAGES = "eu.mikus.edziennik.timetable.RELOAD_PAGES"
        const val DEFAULT_START_HOUR = 6
        const val DEFAULT_END_HOUR = 19
        var pageSelection: Date? = null
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: TimetableFragmentBinding? = null
    private var viewModel: TimetableViewModel? = null

    private val attendanceManager by lazy { AttendanceManager(app) }
    private val attendanceIconFactory: (Context, AttendanceFull) -> android.graphics.drawable.Drawable? = { ctx, att ->
        attendanceManager.getAttendanceIcon(att)?.let { icon ->
            IconicsDrawable(ctx, icon).apply {
                colorInt = attendanceManager.getAttendanceColor(att)
                sizeDp = 24
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = TimetableFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        lifecycleScope.launch {
            val notPublic = app.profile.getStudentData("timetableNotPublic", false)

            val (days, startHour, endHour) = withContext(Dispatchers.Default) {
                val list = mutableListOf<Date>()
                val cursor = app.profile.dateSemester1Start.clone()
                val yearEnd = app.profile.dateYearEnd
                while (cursor.value <= yearEnd.value) {
                    list += cursor.clone()
                    cursor.stepForward(0, 0, 1)
                }

                val lessonRanges = app.db.lessonRangeDao().getAllNow(App.profileId)
                val start = lessonRanges.minOfOrNull { it.startTime.hour } ?: DEFAULT_START_HOUR
                val end = lessonRanges.maxOfOrNull { it.endTime.hour }?.plus(1) ?: DEFAULT_END_HOUR
                Triple(list, start, end)
            }

            if (!isAdded) return@launch
            if (days.isEmpty()) return@launch

            val requestedArg = arguments?.getString("timetableDate", "")
                ?.let { if (it.isBlank()) null else Date.fromY_m_d(it) }
            val initialIndex = days
                .indexOfFirst { it.value == (requestedArg?.value ?: Date.getToday().value) }
                .coerceAtLeast(0)
            pageSelection = days.getOrNull(initialIndex)

            val vm = ViewModelProvider(
                this@TimetableFragment,
                TimetableViewModel.Factory(activity.applicationContext, days[initialIndex], startHour, endHour),
            )[TimetableViewModel::class.java]
            viewModel = vm

            val lessonHeight = app.data.uiConfig.lessonHeight
            val colorSubjectName = app.profile.config.ui.timetableColorSubjectName

            activity.navView.bottomBar.fabExtendedText = getString(R.string.timetable_today)
            activity.navView.bottomBar.fabIcon = SzkolnyFont.Icon.szf_calendar_today_outline
            activity.navView.setFabOnClickListener(View.OnClickListener {
                viewModel?.requestDate(Date.getToday())
            })

            activity.navView.bottomSheet.prependItems(
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_timetable_sync)
                    .withIcon(CommunityMaterial.Icon.cmd_calendar_sync_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        syncWeek(currentWeekStart())
                    }),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.timetable_select_day)
                    .withIcon(SzkolnyFont.Icon.szf_calendar_today_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        MaterialDatePicker.Builder.datePicker()
                            .setSelection((pageSelection ?: Date.getToday()).inMillisUtc)
                            .setCalendarConstraints(app.profile.getSchoolYearConstrains())
                            .build()
                            .apply {
                                addOnPositiveButtonClickListener { millis ->
                                    viewModel?.requestDate(Date.fromMillisUtc(millis))
                                }
                            }
                            .show(activity.supportFragmentManager, TAG)
                    }),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_add_event)
                    .withDescription(R.string.menu_add_event_desc)
                    .withIcon(SzkolnyFont.Icon.szf_calendar_plus_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        EventManualDialog(activity, App.profileId, defaultDate = pageSelection).show()
                    }),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_generate_block_timetable)
                    .withDescription(R.string.menu_generate_block_timetable_desc)
                    .withIcon(CommunityMaterial.Icon3.cmd_table_large)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        GenerateBlockTimetableDialog(activity)
                    }),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_timetable_config)
                    .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        TimetableConfigDialog(activity, false, null, null).show()
                    }),
                BottomSheetSeparatorItem(true),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_mark_as_read)
                    .withIcon(CommunityMaterial.Icon.cmd_eye_check_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        lifecycleScope.launch(Dispatchers.IO) {
                            app.db.metadataDao().setAllSeen(App.profileId, MetadataType.LESSON_CHANGE, true)
                        }
                        Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                    }),
            )

            b.composeView.setAppThemeContent {
                val requested by vm.requestedDate.collectAsStateWithLifecycle()
                val refreshing by app.syncStatus.isRefreshing.collectAsStateWithLifecycle()
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { syncFeature(activity, FeatureType.TIMETABLE, JsonObject("weekStart" to currentWeekStart())) },
                ) {
                    TimetableScreen(
                        notPublic = notPublic,
                        days = days,
                        initialIndex = initialIndex,
                        lessonHeight = lessonHeight,
                        colorSubjectName = colorSubjectName,
                        requestedDate = requested,
                        onRequestConsumed = vm::clearRequestedDate,
                        onPageChanged = { date ->
                            vm.onPageChanged(date)
                            pageSelection = date
                            activity.navView.bottomBar.fabEnable = date.value != Date.getToday().value
                        },
                        dayFlow = vm::dayFlow,
                        onLessonClick = { pl ->
                            vm.markSeen(pl.lesson)
                            LessonDetailsDialog(activity, pl.lesson, pl.attendance).show()
                        },
                        onSyncClick = { weekStart -> syncWeek(weekStart) },
                        attendanceIconFactory = attendanceIconFactory,
                    )
                }
            }
        }
    }

    private fun syncWeek(weekStart: String) {
        EdziennikTask.syncProfile(
            profileId = App.profileId,
            featureTypes = setOf(FeatureType.TIMETABLE),
            arguments = JsonObject("weekStart" to weekStart),
        ).enqueue(activity)
    }

    private fun currentWeekStart() =
        (viewModel?.currentDate?.value ?: pageSelection ?: Date.getToday()).weekStart.stringY_m_d

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, i: Intent) {
            if (!isAdded) return
            when (i.action) {
                ACTION_SCROLL_TO_DATE -> {
                    val dateStr = i.extras?.getString("timetableDate", null) ?: return
                    viewModel?.requestDate(Date.fromY_m_d(dateStr))
                }
                ACTION_RELOAD_PAGES -> {
                    // no-op: dayFlow is reactive, lessons re-emit on re-sync
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ActivityCompat.registerReceiver(
            activity,
            broadcastReceiver,
            IntentFilter().apply {
                addAction(ACTION_SCROLL_TO_DATE)
                addAction(ACTION_RELOAD_PAGES)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onPause() {
        super.onPause()
        runCatching { activity.unregisterReceiver(broadcastReceiver) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
