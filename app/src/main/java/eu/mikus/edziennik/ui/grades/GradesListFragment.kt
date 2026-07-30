/*
 * Copyright (c) Mikolaj Olszewski 2026-6-26.
 */

package eu.mikus.edziennik.ui.grades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Grade
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.databinding.GradesListFragmentBinding
import eu.mikus.edziennik.ext.Bundle
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.base.syncFeature
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.settings.GradesConfigDialog
import eu.mikus.edziennik.utils.models.Date
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem

class GradesListFragment : Fragment() {

    companion object {
        private const val TAG = "GradesFragment"
    }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: GradesListFragmentBinding? = null
    private lateinit var viewModel: GradesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = GradesListFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        val deepLinkSubject = arguments?.getLong("gradesSubjectId") ?: 0L
        viewModel = ViewModelProvider(
            this, GradesViewModel.Factory(activity.applicationContext, deepLinkSubject),
        )[GradesViewModel::class.java]

        activity.gainAttention()

        view.postDelayed({
            if (!isAdded) return@postDelayed
            activity.bottomSheet.prependItems(
                BottomSheetPrimaryItem(true)
                    .withTitle(R.string.menu_grades_config)
                    .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
                    .withOnClickListener(View.OnClickListener {
                        activity.bottomSheet.close()
                        GradesConfigDialog(activity, true, null, null).show()
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

        val ctx = activity
        b.gradesCompose.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val refreshing by app.syncStatus.isRefreshing.collectAsStateWithLifecycle()
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { syncFeature(activity, FeatureType.GRADES) }) {
                GradesScreen(
                    state = state,
                    formatters = GradesFormatters(
                        gradeColor = { Color(app.gradesManager.getGradeColor(it)) },
                        averageText = { snap -> app.gradesManager.getAverageString(ctx, snap.toGradesAverages())?.toString() },
                        semesterAverageText = { snap, n ->
                            app.gradesManager.getAverageString(ctx, snap.toGradesAverages(), nameSemester = true, showSemester = n)?.toString()
                        },
                        yearAverageText = { snap ->
                            app.gradesManager.getAverageString(ctx, snap.toGradesAverages(), nameSemester = true)?.toString()
                        },
                        yearSummaryText = { count, snap -> app.gradesManager.getYearSummaryString(ctx, count, snap.toGradesAverages()) },
                        weightText = { app.gradesManager.getWeightString(ctx, it, showClassAverage = true)?.toString() },
                        gradeDateText = ::gradeDateText,
                    ),
                    onSubjectToggle = viewModel::toggleSubject,
                    onSemesterToggle = viewModel::toggleSemester,
                    onGradeClick = ::onGradeClick,
                    onEditorClick = ::onEditorClick,
                    onItemSeen = viewModel::markSeen,
                )
            }
        }
    }

    private fun onGradeClick(grade: GradeFull) {
        GradeDetailsDialog(activity, grade).show()
    }

    private fun gradeDateText(grade: Grade): String? {
        if (grade.addedDate == 0L || grade.type == Grade.TYPE_NO_GRADE) return null
        val d = Date.fromMillis(grade.addedDate)
        return d.getRelativeString(app, 5) ?: d.formattedStringShort
    }

    private fun onEditorClick(subjectId: Long, semester: Int) {
        val args = viewModel.editorArgs(subjectId, semester) ?: return
        activity.navigate(navTarget = NavTarget.GRADES_EDITOR, args = Bundle(
            "subjectId" to args.subjectId,
            "semester" to args.semester,
            "averageMode" to args.averageMode,
            "yearAverageBefore" to args.yearAverageBefore,
            "gradeSumOtherSemester" to args.gradeSumOtherSemester,
            "gradeCountOtherSemester" to args.gradeCountOtherSemester,
            "averageOtherSemester" to args.averageOtherSemester,
            "finalOtherSemester" to args.finalOtherSemester,
        ))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
