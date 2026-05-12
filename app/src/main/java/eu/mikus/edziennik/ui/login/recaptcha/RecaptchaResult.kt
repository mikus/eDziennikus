/*
 * Copyright (c) Kuba Szczodrzyński 2023-3-24.
 */

package eu.mikus.edziennik.ui.login.recaptcha

data class RecaptchaResult(
    val isError: Boolean,
    val code: String?,
)
