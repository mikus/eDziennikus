/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.grades

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.button.MaterialButton
import com.mikepenz.iconics.view.IconicsTextView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.GradeFull
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.ui.dialogs.settings.GradesConfigDialog
import eu.mikus.edziennik.ui.notes.setupNotesButton
import eu.mikus.edziennik.utils.BetterLink
import eu.mikus.edziennik.utils.managers.NoteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GradeDetailsDialog(
    activity: AppCompatActivity,
    private val grade: GradeFull,
    private val showNotes: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "GradeDetailsDialog"
    override fun getTitleRes(): Int? = null
    override fun getPositiveButtonText() = R.string.close

    @Composable
    override fun Content() {
        val app = activity.applicationContext as App
        val manager = app.gradesManager
        val history by produceState(initialValue = emptyList<GradeFull>()) {
            value = withContext(Dispatchers.IO) {
                app.db.gradeDao().getByParentIdNow(App.profileId, grade.id)
                    .also { it.forEach { g -> g.filterNotes() } }
            }
        }
        GradeDetailsContent(
            grade = grade,
            gradeColor = Color(manager.getGradeColor(grade)),
            weightText = manager.getWeightString(activity, grade),
            gradeValue = if (grade.weight == 0f || grade.value < 0f) null else manager.getGradeValue(grade),
            history = history,
            historyColor = { Color(manager.getGradeColor(it)) },
            showCustomValue = manager.plusValue != null || manager.minusValue != null,
            showNotes = showNotes,
            activity = activity,
            onCustomValue = { GradesConfigDialog(activity, reloadOnDismiss = true).show() },
            onHistoryClick = { GradeDetailsDialog(activity, it).show() },
            onTeacherAction = dialog::dismiss,
            onShowListener = onShowListener,
            onDismissListener = onDismissListener,
        )
    }
}

@Composable
private fun GradeDetailsContent(
    grade: GradeFull,
    gradeColor: Color,
    weightText: String?,
    gradeValue: Float?,
    history: List<GradeFull>,
    historyColor: (GradeFull) -> Color,
    showCustomValue: Boolean,
    showNotes: Boolean,
    activity: AppCompatActivity,
    onCustomValue: () -> Unit,
    onHistoryClick: (GradeFull) -> Unit,
    onTeacherAction: () -> Unit,
    onShowListener: ((tag: String) -> Unit)?,
    onDismissListener: ((tag: String) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Name pill + value/weight
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(gradeColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = grade.name,
                    color = if (gradeColor.luminance() > 0.3f) Color(0xAA000000) else Color(0xCCFFFFFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (weightText != null) {
                    Text(weightText, style = MaterialTheme.typography.bodyMedium)
                }
                if (gradeValue != null) {
                    Text(
                        stringResource(
                            R.string.grades_value_format,
                            "%.02f".format(gradeValue),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Notes legend (top, mirrors legacy) — setLegendText hides itself when there are no notes.
        if (showNotes) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx -> IconicsTextView(ctx) },
                update = { tv -> NoteManager.setLegendText(grade, tv) },
            )
        }

        // Teacher line (BetterLink linkified, dismisses on action)
        grade.teacherName?.let { name ->
            Text(
                stringResource(R.string.dialog_grade_details_teacher),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    TextView(ctx).apply {
                        text = name
                        BetterLink.attach(
                            this,
                            teachers = mapOf(grade.teacherId to name),
                            onActionSelected = onTeacherAction,
                        )
                    }
                },
            )
        }

        // Child-grade history (recursion: tap opens another GradeDetailsDialog)
        if (history.isNotEmpty()) {
            Text(
                stringResource(R.string.dialog_grade_details_history),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(history, key = { it.id }) { g ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHistoryClick(g) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GradePill(g, historyColor(g))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            g.category ?: g.description ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        // Custom +/- value notice + shortcut to the config dialog
        if (showCustomValue) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.grades_stats_custom_value_notice),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onCustomValue) {
                    Text(stringResource(R.string.configure))
                }
            }
        }

        // Notes button (bottom, mirrors legacy). Wrapped in a LinearLayout because
        // setupNotesButton() casts its layoutParams to LinearLayout.LayoutParams.
        if (showNotes) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        val button = MaterialButton(ctx)
                        addView(
                            button,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                        button.setupNotesButton(
                            activity = activity,
                            owner = grade,
                            onShowListener = onShowListener,
                            onDismissListener = onDismissListener,
                        )
                    }
                },
            )
        }
    }
}
