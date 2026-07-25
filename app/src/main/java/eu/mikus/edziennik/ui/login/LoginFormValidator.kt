/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import androidx.annotation.StringRes

/**
 * Pure per-field clean + validate for the login credential form. Mirrors the per-field pipeline in
 * the legacy [LoginFormFragment.login] exactly: clean (trim unless hideText, case, strip), then the
 * required/regex checks. [Result.cleaned] is always the normalized value (so the screen can mirror
 * login()'s unconditional setText(cleaned) even on an invalid field); [Result.errorRes] is null iff
 * the field is valid. No Android deps — unit-testable on the JVM.
 */
object LoginFormValidator {

    data class Result(val cleaned: String, @StringRes val errorRes: Int?)

    fun validate(field: LoginInfo.FormField, raw: String): Result {
        var text = raw
        if (!field.hideText) text = text.trim()
        when (field.caseMode) {
            LoginInfo.FormField.CaseMode.UPPER_CASE -> text = text.uppercase()
            LoginInfo.FormField.CaseMode.LOWER_CASE -> text = text.lowercase()
            LoginInfo.FormField.CaseMode.UNCHANGED -> {}
        }
        field.stripTextRegex?.let { text = text.replace(it.toRegex(), "") }

        return when {
            field.isRequired && text.isBlank() -> Result(text, field.emptyText)
            !text.matches(field.validationRegex.toRegex()) -> Result(text, field.invalidText)
            else -> Result(text, null)
        }
    }
}
