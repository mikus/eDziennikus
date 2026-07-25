/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ext.resolveString
import eu.mikus.edziennik.ui.compose.IconicsIcon

/** Result of a submit: cleaned values of valid fields, checkbox values, and whether any field failed. */
class FormSubmit(
    val fieldValues: Map<String, String>,
    val checkboxValues: Map<String, Boolean>,
    val hasErrors: Boolean,
)

@Composable
fun LoginFormScreen(
    register: LoginInfo.Register,
    mode: LoginInfo.Mode,
    initialValues: Map<String, String>,
    initialChecks: Map<String, Boolean>,
    initialFieldErrors: Map<String, Int>,
    initialBannerError: Int?,
    onBack: () -> Unit,
    onSubmit: (FormSubmit) -> Unit,
    onQrScan: (field: LoginInfo.FormField, apply: (Map<String, String>) -> Unit) -> Unit,
) {
    val fields = remember(mode) { mode.credentials.filterIsInstance<LoginInfo.FormField>() }
    val checkboxes = remember(mode) { mode.credentials.filterIsInstance<LoginInfo.FormCheckbox>() }
    val fieldKeys = remember(mode) { fields.map { it.keyName } }

    // Rotation-safe field values (list-saver keyed by field order).
    val values = rememberSaveable(
        saver = listSaver(
            save = { map -> fieldKeys.map { map[it] ?: "" } },
            restore = { saved -> mutableStateMapOf<String, String>().apply { fieldKeys.forEachIndexed { i, k -> put(k, saved.getOrElse(i) { "" }) } } },
        ),
    ) { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.keyName, initialValues[it.keyName] ?: "") } } }

    val checks = remember { mutableStateMapOf<String, Boolean>().apply { checkboxes.forEach { put(it.keyName, initialChecks[it.keyName] ?: it.checked) } } }
    val fieldErrors = remember { mutableStateMapOf<String, Int?>().apply { putAll(initialFieldErrors) } }
    var bannerError by remember { androidx.compose.runtime.mutableStateOf(initialBannerError) }

    fun submit() {
        var hasErrors = false
        val validValues = LinkedHashMap<String, String>()
        for (field in fields) {
            val r = LoginFormValidator.validate(field, values[field.keyName] ?: "")
            values[field.keyName] = r.cleaned            // mirror legacy setText(cleaned), even on error
            if (r.errorRes != null) {
                fieldErrors[field.keyName] = r.errorRes
                hasErrors = true
            } else {
                fieldErrors[field.keyName] = null
                validValues[field.keyName] = r.cleaned
            }
        }
        val checkboxValues = checkboxes.associate { it.keyName to (checks[it.keyName] ?: false) }
        onSubmit(FormSubmit(validValues, checkboxValues, hasErrors))
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            IconicsIcon(
                icon = CommunityMaterial.Icon.cmd_account_circle_outline,
                contentDescription = null,
                sizeDp = 32,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = stringResource(R.string.login_form_title_format, stringResource(register.registerName)),
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(stringResource(mode.name), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(mode.guideText), modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))

            bannerError?.let { ErrorBanner(stringResource(it)) }

            fields.forEach { field ->
                key(field.keyName) {  // distinct composition identity per field (isolates each field's state)
                    FormFieldRow(
                        field = field,
                        value = values[field.keyName] ?: "",
                        errorRes = fieldErrors[field.keyName],
                        onValueChange = { values[field.keyName] = it; fieldErrors[field.keyName] = null },
                        onQrScan = { onQrScan(field) { decoded -> decoded.forEach { (k, v) -> values[k] = v } } },
                    )
                }
            }
            checkboxes.forEach { cb ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checks[cb.keyName] ?: false, onCheckedChange = { checks[cb.keyName] = it })
                    Text(stringResource(cb.name))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.back))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { submit() }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.login_button))
            }
        }
    }
}

@Composable
private fun FormFieldRow(
    field: LoginInfo.FormField,
    value: String,
    errorRes: Int?,
    onValueChange: (String) -> Unit,
    onQrScan: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var passwordVisible by rememberSaveable(field.keyName) { androidx.compose.runtime.mutableStateOf(false) }
    val keyboardType = if (field.isNumber) KeyboardType.Number else if (field.hideText) KeyboardType.Password else KeyboardType.Text
    val visual = if (field.hideText && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(field.name.resolveString(context)) },
        singleLine = true,
        isError = errorRes != null,
        supportingText = errorRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visual,
        prefix = field.prefix?.let { { Text(stringResource(it)) } },
        suffix = field.suffix?.let { { Text(stringResource(it)) } },
        leadingIcon = { IconicsIcon(icon = field.icon, contentDescription = null) },
        trailingIcon = when {
            field.hideText -> {
                {
                    val icon = if (passwordVisible) CommunityMaterial.Icon.cmd_eye_off_outline else CommunityMaterial.Icon.cmd_eye_outline
                    androidx.compose.material3.IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        IconicsIcon(icon = icon, contentDescription = null)
                    }
                }
            }
            field.qrDecoderClass != null -> {
                {
                    androidx.compose.material3.IconButton(onClick = onQrScan) {
                        IconicsIcon(icon = CommunityMaterial.Icon3.cmd_qrcode, contentDescription = null)
                    }
                }
            }
            else -> null
        },
    )
}

@Composable
private fun ErrorBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp)
            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconicsIcon(
            icon = CommunityMaterial.Icon.cmd_alert_circle_outline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
        )
        Spacer(Modifier.size(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onError)
    }
}
