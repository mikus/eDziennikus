/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog

class AgendaConfigDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "AgendaConfigDialog"
    override fun getTitleRes() = R.string.menu_agenda_config
    override fun getPositiveButtonText() = R.string.ok

    @Composable
    override fun Content() = AgendaConfigContent(activity.applicationContext as App)

    override fun onDismiss() {
        if (reloadOnDismiss && activity is MainActivity) activity.reloadTarget()
    }
}

@Composable
private fun AgendaConfigContent(app: App) {
    val cfg = app.profile.config.ui
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionHeader(R.string.config_appearance)
        var lessonChanges by remember { mutableStateOf(cfg.agendaLessonChanges) }
        CheckboxRow(R.string.agenda_config_lesson_changes, lessonChanges) { lessonChanges = it; cfg.agendaLessonChanges = it }
        var teacherAbsence by remember { mutableStateOf(cfg.agendaTeacherAbsence) }
        CheckboxRow(R.string.agenda_config_teacher_absence, teacherAbsence) { teacherAbsence = it; cfg.agendaTeacherAbsence = it }
        var subjectImportant by remember { mutableStateOf(cfg.agendaSubjectImportant) }
        CheckboxRow(R.string.agenda_config_subject_important, subjectImportant) { subjectImportant = it; cfg.agendaSubjectImportant = it }
    }
}
