/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.IconicsIcon
import eu.mikus.edziennik.ui.dialogs.base.ComposeDialog
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.COLOR_MODE_DEFAULT
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.COLOR_MODE_WEIGHTED
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.ORDER_BY_DATE_DESC
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.ORDER_BY_SUBJECT_ASC
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_AVG_2_AVG
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_AVG_2_SEM
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_SEM_2_AVG
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_1_SEM_2_SEM
import eu.mikus.edziennik.utils.managers.GradesManager.Companion.YEAR_ALL_GRADES
import java.util.Locale

/**
 * Grades configuration dialog, now a [ComposeDialog] launcher. Keeps the exact `.show()` + ctor
 * contract of the legacy `ConfigDialog<DialogConfigGradesBinding>` so all four callers are untouched.
 * Every control writes its change straight back to config (live), so [onDismiss] only needs to
 * trigger the target reload — mirroring the legacy load-on-show / save-on-dismiss end state.
 */
class GradesConfigDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ComposeDialog(activity, onShowListener, onDismissListener) {

    override val TAG = "GradesConfigDialog"
    override fun getTitleRes() = R.string.menu_grades_config
    override fun getPositiveButtonText() = R.string.ok

    @Composable
    override fun Content() {
        GradesConfigContent(app = activity.applicationContext as App)
    }

    override fun onDismiss() {
        // config is written live by the controls; only the reload is needed on dismiss (mirrors ConfigDialog)
        if (reloadOnDismiss && activity is MainActivity) activity.reloadTarget()
    }
}

@Composable
private fun GradesConfigContent(app: App) {
    val context = LocalContext.current
    val globalConfig = app.config.grades          // GLOBAL: orderBy
    val profileConfig = app.profile.config.grades  // PROFILE: the rest

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.grades_config_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Custom +/- value (null when unchecked)
        var customPlus by remember { mutableStateOf(profileConfig.plusValue != null) }
        var plusValue by remember { mutableFloatStateOf(profileConfig.plusValue ?: 0.5f) }
        CustomValueRow(
            labelRes = R.string.grades_config_plus_value,
            checked = customPlus,
            value = plusValue,
            onCheckedChange = {
                customPlus = it
                profileConfig.plusValue = if (it) plusValue else null
            },
            onValueChange = {
                plusValue = it
                if (customPlus) profileConfig.plusValue = it
            },
        )

        var customMinus by remember { mutableStateOf(profileConfig.minusValue != null) }
        var minusValue by remember { mutableFloatStateOf(profileConfig.minusValue ?: 0.25f) }
        CustomValueRow(
            labelRes = R.string.grades_config_minus_value,
            checked = customMinus,
            value = minusValue,
            onCheckedChange = {
                customMinus = it
                profileConfig.minusValue = if (it) minusValue else null
            },
            onValueChange = {
                minusValue = it
                if (customMinus) profileConfig.minusValue = it
            },
        )

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // Don't count grades + comma-separated names
        var dontCount by remember {
            mutableStateOf(profileConfig.dontCountEnabled && profileConfig.dontCountGrades.isNotEmpty())
        }
        var dontCountText by remember {
            mutableStateOf(
                profileConfig.dontCountGrades.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "nb, 0, bz, bd",
            )
        }
        fun saveDontCount() {
            profileConfig.dontCountEnabled = dontCount
            profileConfig.dontCountGrades = dontCountText.lowercase().split(",").map { it.trim() }
        }
        CheckboxRow(
            labelRes = R.string.grades_config_dont_count_grades,
            checked = dontCount,
            onCheckedChange = { dontCount = it; saveDontCount() },
        )
        OutlinedTextField(
            value = dontCountText,
            onValueChange = { dontCountText = it; saveDontCount() },
            enabled = dontCount,
            singleLine = true,
            label = { Text(stringResource(R.string.grades_config_dont_count_hint)) },
            placeholder = { Text(stringResource(R.string.grades_config_dont_count_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Hide improved
        var hideImproved by remember { mutableStateOf(profileConfig.hideImproved) }
        CheckboxRow(R.string.grades_config_dont_show_improved, hideImproved) {
            hideImproved = it
            profileConfig.hideImproved = it
        }

        // Average without weight + help
        var averageWithoutWeight by remember { mutableStateOf(profileConfig.averageWithoutWeight) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckboxRow(
                labelRes = R.string.grades_config_average_without_weight,
                checked = averageWithoutWeight,
                modifier = Modifier.weight(1f),
                onCheckedChange = { averageWithoutWeight = it; profileConfig.averageWithoutWeight = it },
            )
            IconButton(onClick = {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.grades_config_average_without_weight)
                    .setMessage(R.string.grades_config_average_without_weight_message)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }) {
                IconicsIcon(
                    CommunityMaterial.Icon2.cmd_help_circle_outline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // Sort mode (GLOBAL orderBy)
        SectionHeader(R.string.menu_grades_sort_mode)
        var orderBy by remember { mutableIntStateOf(globalConfig.orderBy) }
        RadioRow(R.string.dialog_grades_config_sort_by_date, orderBy == ORDER_BY_DATE_DESC) {
            orderBy = ORDER_BY_DATE_DESC
            globalConfig.orderBy = ORDER_BY_DATE_DESC
        }
        RadioRow(R.string.dialog_grades_config_sort_by_subject, orderBy == ORDER_BY_SUBJECT_ASC) {
            orderBy = ORDER_BY_SUBJECT_ASC
            globalConfig.orderBy = ORDER_BY_SUBJECT_ASC
        }

        // Color mode
        SectionHeader(R.string.menu_grades_color_mode)
        var colorMode by remember { mutableIntStateOf(profileConfig.colorMode) }
        RadioRow(R.string.dialog_grades_config_color_from_eregister, colorMode == COLOR_MODE_DEFAULT) {
            colorMode = COLOR_MODE_DEFAULT
            profileConfig.colorMode = COLOR_MODE_DEFAULT
        }
        RadioRow(R.string.dialog_grades_config_color_by_value, colorMode == COLOR_MODE_WEIGHTED) {
            colorMode = COLOR_MODE_WEIGHTED
            profileConfig.colorMode = COLOR_MODE_WEIGHTED
        }

        // Year average mode (5 options)
        SectionHeader(R.string.menu_grades_average_mode)
        var yearMode by remember { mutableIntStateOf(profileConfig.yearAverageMode) }
        RadioRow(R.string.settings_register_avg_mode_4, yearMode == YEAR_ALL_GRADES) {
            yearMode = YEAR_ALL_GRADES
            profileConfig.yearAverageMode = YEAR_ALL_GRADES
        }
        RadioRow(R.string.settings_register_avg_mode_0, yearMode == YEAR_1_AVG_2_AVG) {
            yearMode = YEAR_1_AVG_2_AVG
            profileConfig.yearAverageMode = YEAR_1_AVG_2_AVG
        }
        RadioRow(R.string.settings_register_avg_mode_1, yearMode == YEAR_1_SEM_2_AVG) {
            yearMode = YEAR_1_SEM_2_AVG
            profileConfig.yearAverageMode = YEAR_1_SEM_2_AVG
        }
        RadioRow(R.string.settings_register_avg_mode_2, yearMode == YEAR_1_AVG_2_SEM) {
            yearMode = YEAR_1_AVG_2_SEM
            profileConfig.yearAverageMode = YEAR_1_AVG_2_SEM
        }
        RadioRow(R.string.settings_register_avg_mode_3, yearMode == YEAR_1_SEM_2_SEM) {
            yearMode = YEAR_1_SEM_2_SEM
            profileConfig.yearAverageMode = YEAR_1_SEM_2_SEM
        }
    }
}

@Composable
private fun SectionHeader(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun RadioRow(labelRes: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckboxRow(
    labelRes: Int,
    checked: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CustomValueRow(
    labelRes: Int,
    checked: Boolean,
    value: Float,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(
                stringResource(labelRes),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (checked) {
                Text(
                    String.format(Locale.getDefault(), "%.2f", value),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (checked) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
