/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-15.
 */

package eu.mikus.edziennik.ui.login.oauth

data class OAuthLoginResult(
    val isError: Boolean,
    val responseUrl: String?,
)
