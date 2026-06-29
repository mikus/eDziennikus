/*
 * Copyright (c) Mikolaj Olszewski 2026-6-29.
 */

package eu.mikus.edziennik.ui.attendance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.AttendanceFragmentBinding
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.AttendanceConfigDialog
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem

class AttendanceFragment : Fragment() {
    companion object {
        private const val TAG = "AttendanceFragment"
        const val VIEW_SUMMARY = 0
        const val VIEW_DAYS = 1
        const val VIEW_MONTHS = 2
        const val VIEW_TYPES = 3
        const val VIEW_LIST = 4
        var pageSelection = 1
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: AttendanceFragmentBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = AttendanceFragmentBinding.inflate(inflater, container, false)
        b = binding
        binding.refreshLayout.setParent(activity.swipeRefreshLayout)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        val viewModel = ViewModelProvider(
            this, AttendanceViewModel.Factory(activity.applicationContext),
        )[AttendanceViewModel::class.java]

        activity.gainAttention()

        // Defer the prepend one navlib settle-pass past onViewCreated so it lands after navlib's
        // post-navigation bottom-sheet reset (the legacy fragment used startCoroutineTimer(100L)).
        view.postDelayed({
            if (!isAdded) return@postDelayed
            activity.bottomSheet.prependItems(
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_attendance_config)
                    .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        AttendanceConfigDialog(activity, true, null, null).show()
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

        if (pageSelection == 1)
            pageSelection = app.profile.config.attendance.attendancePageSelection

        val manager = app.attendanceManager
        b.composeView.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val period by viewModel.period.collectAsStateWithLifecycle()
            AttendanceScreen(
                state = state,
                period = period,
                useSymbols = manager.useSymbols,
                colorForAttendance = { Color(manager.getAttendanceColor(it)) },
                colorForType = { Color(manager.getAttendanceColor(it)) },
                icon = { manager.getAttendanceIcon(it) },
                onPeriodChange = viewModel::setPeriod,
                onNodeToggle = viewModel::toggleNode,
                onLeafClick = { AttendanceDetailsDialog(activity, it).show() },
                onItemSeen = viewModel::markSeen,
                setRefreshEnabled = { b.refreshLayout.isEnabled = it },
                initialPage = pageSelection,
                onPageChange = {
                    pageSelection = it
                    app.profile.config.attendance.attendancePageSelection = it
                },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
