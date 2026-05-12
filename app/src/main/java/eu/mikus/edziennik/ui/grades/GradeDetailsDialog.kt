package eu.mikus.edziennik.ui.grades

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.databinding.DialogGradeDetailsBinding
import eu.mikus.edziennik.ext.onClick
import eu.mikus.edziennik.ext.setTintColor
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.ui.dialogs.settings.GradesConfigDialog
import eu.mikus.edziennik.ui.notes.setupNotesButton
import eu.mikus.edziennik.utils.BetterLink
import eu.mikus.edziennik.utils.SimpleDividerItemDecoration
import eu.mikus.edziennik.utils.managers.NoteManager

class GradeDetailsDialog(
    activity: AppCompatActivity,
    private val grade: GradeFull,
    private val showNotes: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<DialogGradeDetailsBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "GradeDetailsDialog"

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogGradeDetailsBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close

    override suspend fun onShow() {
        val manager = app.gradesManager

        val gradeColor = manager.getGradeColor(grade)
        b.grade = grade
        b.weightText = manager.getWeightString(app, grade)
        b.commentVisible = false
        b.devMode = App.devMode
        b.gradeName.setTextColor(
            if (ColorUtils.calculateLuminance(gradeColor) > 0.3)
                0xaa000000.toInt()
            else
                0xccffffff.toInt()
        )
        b.gradeName.background.setTintColor(gradeColor)

        b.gradeValue = if (grade.weight == 0f || grade.value < 0f)
            -1f
        else
            manager.getGradeValue(grade)

        b.customValueDivider.isVisible = manager.plusValue != null || manager.minusValue != null
        b.customValueLayout.isVisible = b.customValueDivider.isVisible
        b.customValueButton.onClick {
            GradesConfigDialog(activity, reloadOnDismiss = true).show()
        }

        grade.teacherName?.let { name ->
            BetterLink.attach(
                b.teacherName,
                teachers = mapOf(grade.teacherId to name),
                onActionSelected = dialog::dismiss
            )
        }

        val historyList = withContext(Dispatchers.Default) {
            app.db.gradeDao().getByParentIdNow(App.profileId, grade.id)
        }

        historyList.forEach {
            it.filterNotes()
        }

        b.historyVisible = historyList.isNotEmpty()
        if (historyList.isNotEmpty()) {
            b.gradeHistoryList.adapter = GradesAdapter(activity, onGradeClick = {
                GradeDetailsDialog(activity, it).show()
            }).also {
                it.items = historyList.toMutableList()
            }

            b.gradeHistoryList.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(SimpleDividerItemDecoration(context))
            }
        }

        b.notesButton.isVisible = showNotes
        b.notesButton.setupNotesButton(
            activity = activity,
            owner = grade,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        )
        b.legend.isVisible = showNotes
        if (showNotes)
            NoteManager.setLegendText(grade, b.legend)
    }
}
