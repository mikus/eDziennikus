/*
 * Copyright (c) Mikolaj Olszewski 2026-6-30.
 */

package eu.mikus.edziennik.ui.agenda

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.AgendaFragmentBinding
import eu.mikus.edziennik.ui.agenda.lessonchanges.LessonChangesDialog
import eu.mikus.edziennik.ui.agenda.teacherabsence.TeacherAbsenceDialog
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.AgendaConfigDialog
import eu.mikus.edziennik.ui.event.EventDetailsDialog
import eu.mikus.edziennik.ui.event.EventManualDialog
import eu.szkolny.font.SzkolnyFont
import java.time.YearMonth
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem

class AgendaFragment : Fragment() {
    companion object {
        private const val TAG = "AgendaFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: AgendaFragmentBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = AgendaFragmentBinding.inflate(inflater, container, false)
        b = binding
        binding.refreshLayout.setParent(activity.swipeRefreshLayout)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        val viewModel = ViewModelProvider(
            this, AgendaViewModel.Factory(activity.applicationContext),
        )[AgendaViewModel::class.java]

        activity.gainAttention()

        view.postDelayed({
            if (!isAdded) return@postDelayed
            activity.bottomSheet.prependItems(
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_add_event)
                    .withDescription(R.string.menu_add_event_desc)
                    .withIcon(SzkolnyFont.Icon.szf_calendar_plus_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        EventManualDialog(activity, App.profileId, defaultDate = viewModel.selectedDate.value).show()
                    }),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_agenda_config)
                    .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        AgendaConfigDialog(activity, true, null, null).show()
                    }),
                BottomSheetSeparatorItem(true),
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_mark_as_read)
                    .withIcon(CommunityMaterial.Icon.cmd_eye_check_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        viewModel.markAllSeen()
                        Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                    }),
            )
        }, 100)

        activity.navView.bottomBar.fabEnable = true
        activity.navView.bottomBar.fabExtendedText = getString(R.string.add)
        activity.navView.bottomBar.fabIcon = CommunityMaterial.Icon3.cmd_plus
        activity.navView.setFabOnClickListener {
            EventManualDialog(activity, App.profileId, defaultDate = viewModel.selectedDate.value).show()
        }
        activity.gainAttentionFAB()

        val startMonth = YearMonth.of(app.profile.dateSemester1Start.year, app.profile.dateSemester1Start.month)
        val endMonth = YearMonth.of(app.profile.dateYearEnd.year, app.profile.dateYearEnd.month)

        b.composeView.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AgendaScreen(
                state = state,
                startMonth = startMonth,
                endMonth = endMonth,
                onDaySelected = viewModel::setSelectedDate,
                onEventClick = { EventDetailsDialog(activity, it).show() },
                onEventEditClick = { EventManualDialog(activity, it.profileId, editingEvent = it).show() },
                onItemSeen = viewModel::markSeen,
                onLessonChangesClick = { LessonChangesDialog(activity, App.profileId, defaultDate = it).show() },
                onTeacherAbsenceClick = { TeacherAbsenceDialog(activity, App.profileId, date = it).show() },
                setRefreshEnabled = { b.refreshLayout.isEnabled = it },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
