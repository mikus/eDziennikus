/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades.editor

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.GradesEditorFragmentBinding
import eu.mikus.edziennik.ext.getFloat
import eu.mikus.edziennik.ext.getInt
import eu.mikus.edziennik.ext.getLong
import eu.mikus.edziennik.ext.input
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.grades.GradesEditorArgs
import eu.mikus.edziennik.utils.Colors

class GradesEditorFragment : Fragment() {
    companion object { private const val TAG = "GradesEditorFragment" }

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private var b: GradesEditorFragmentBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = GradesEditorFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        val args = GradesEditorArgs(
            subjectId = arguments.getLong("subjectId", -1),
            semester = arguments.getInt("semester", 1),
            averageMode = arguments.getInt("averageMode", -1),
            yearAverageBefore = arguments.getFloat("yearAverageBefore", -1f),
            gradeSumOtherSemester = arguments.getFloat("gradeSumOtherSemester", -1f),
            gradeCountOtherSemester = arguments.getFloat("gradeCountOtherSemester", -1f),
            averageOtherSemester = arguments.getFloat("averageOtherSemester", -1f),
            finalOtherSemester = arguments.getFloat("finalOtherSemester", -1f),
        )

        val viewModel = ViewModelProvider(
            this, GradesEditorViewModel.Factory(app, args),
        )[GradesEditorViewModel::class.java]

        b.composeView.setAppThemeContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            // SubjectMissing → show the legacy error dialog + navigate up
            LaunchedEffect(state) {
                if (state is GradesEditorUiState.SubjectMissing) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.error_occured)
                        .setMessage(R.string.error_no_subject_id)
                        .setPositiveButton(R.string.ok) { _, _ -> activity.navigateUp() }
                        .show()
                }
            }
            GradesEditorScreen(
                state = state,
                gradeColor = { Color(Colors.gradeNameToColor(it)) },
                onEditName = { id, option -> viewModel.edit(id, name = option.name, value = option.value) },
                onEditWeight = { id, weight -> viewModel.edit(id, weight = weight) },
                onRemove = viewModel::remove,
                onAdd = { option, weight -> viewModel.add(EditorGrade(System.currentTimeMillis(), option.name, option.value, getString(R.string.grades_editor_new_grade), weight)) },
                onRestore = viewModel::restore,
                onCustomWeight = { onPicked -> promptCustomWeight(onPicked) },
            )
        }
    }

    private fun promptCustomWeight(onPicked: (Float) -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.grades_editor_add_grade_title)
            .input(
                message = getString(R.string.grades_editor_add_grade_weight),
                type = InputType.TYPE_NUMBER_FLAG_SIGNED,
                positiveButton = R.string.ok,
                positiveListener = { _, input -> input.toFloatOrNull()?.let(onPicked); true },
            )
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }
}
