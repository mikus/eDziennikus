/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.gson.JsonObject
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.*
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ui.grades.models.ExpandableItemModel
import eu.mikus.edziennik.ui.login.qr.LoginLibrusQrDecoder
import eu.mikus.edziennik.ui.login.qr.LoginQrDecoder
import pl.szczodrzynski.fslogin.realm.RealmData

object LoginInfo {

    private fun getEmailCredential(keyName: String) = FormField(
        keyName = keyName,
        name = R.string.login_hint_email,
        icon = CommunityMaterial.Icon.cmd_at,
        emptyText = R.string.login_error_no_email,
        invalidText = R.string.login_error_incorrect_email,
        errorCodes = mapOf(),
        isRequired = true,
        validationRegex = "([\\w.\\-_+]+)?\\w+@[\\w-_]+(\\.\\w+)+",
        caseMode = FormField.CaseMode.LOWER_CASE
    )

    private fun getPasswordCredential(keyName: String) = FormField(
        keyName = keyName,
        name = R.string.login_hint_password,
        icon = CommunityMaterial.Icon2.cmd_lock_outline,
        emptyText = R.string.login_error_no_password,
        invalidText = R.string.login_error_incorrect_login_or_password,
        errorCodes = mapOf(),
        isRequired = true,
        validationRegex = ".*",
        hideText = true
    )

    val list by lazy {
        listOf(
            Register(
                loginType = LoginType.LIBRUS,
                registerName = R.string.login_register_librus,
                registerLogo = R.drawable.login_logo_librus,
                loginModes = listOf(
                    Mode(
                        loginMode = LoginMode.LIBRUS_EMAIL,
                        name = R.string.login_mode_librus_email,
                        icon = R.drawable.login_mode_librus_email,
                        hintText = R.string.login_mode_librus_email_hint,
                        guideText = R.string.login_mode_librus_email_guide,
                        isRecommended = true,
                        credentials = listOf(
                            getEmailCredential("email"),
                            getPasswordCredential("password")
                        ),
                        errorCodes = mapOf(
                            ERROR_LOGIN_LIBRUS_PORTAL_NOT_ACTIVATED to R.string.login_error_account_not_activated,
                            ERROR_LOGIN_LIBRUS_PORTAL_INVALID_LOGIN to R.string.login_error_incorrect_login_or_password,
                        )
                    ),
                    /*Mode(
                            loginMode = LoginMode.LIBRUS_SYNERGIA,
                            name = R.string.login_mode_librus_synergia,
                            icon = R.drawable.login_mode_librus_synergia,
                            hintText = R.string.login_mode_librus_synergia_hint,
                            guideText = R.string.login_mode_librus_synergia_guide,
                            credentials = listOf(
                                    Credential(
                                            keyName = "accountLogin",
                                            name = R.string.login_hint_login,
                                            icon = CommunityMaterial.Icon.cmd_account_outline,
                                            emptyText = R.string.login_error_no_login,
                                            invalidText = R.string.login_error_incorrect_login,
                                            errorCodes = mapOf(),
                                            isRequired = true,
                                            validationRegex = "[A-z0-9._\\-+]+",
                                            caseMode = Credential.CaseMode.LOWER_CASE
                                    ),
                                    getPasswordCredential("accountPassword")
                            ),
                            errorCodes = mapOf(
                                    ERROR_LOGIN_LIBRUS_API_INVALID_LOGIN to R.string.login_error_incorrect_login_or_password,
                                    ERROR_LOGIN_LIBRUS_API_INVALID_REQUEST to R.string.login_error_incorrect_login_or_password
                            )
                    ),*/
                    Mode(
                        loginMode = LoginMode.LIBRUS_JST,
                        name = R.string.login_mode_librus_jst,
                        icon = R.drawable.login_mode_librus_jst,
                        hintText = R.string.login_mode_librus_jst_hint,
                        guideText = R.string.login_mode_librus_jst_guide,
                        credentials = listOf(
                            FormField(
                                keyName = "accountCode",
                                name = R.string.login_hint_token,
                                icon = CommunityMaterial.Icon.cmd_code_braces,
                                emptyText = R.string.login_error_no_token,
                                invalidText = R.string.login_error_incorrect_token,
                                errorCodes = mapOf(),
                                isRequired = true,
                                validationRegex = "[A-Z0-9_]+",
                                caseMode = FormField.CaseMode.UPPER_CASE,
                                qrDecoderClass = LoginLibrusQrDecoder::class.java
                            ),
                            FormField(
                                keyName = "accountPin",
                                name = R.string.login_hint_pin,
                                icon = CommunityMaterial.Icon2.cmd_lock_outline,
                                emptyText = R.string.login_error_no_pin,
                                invalidText = R.string.login_error_incorrect_pin,
                                errorCodes = mapOf(),
                                isRequired = true,
                                validationRegex = "[a-z0-9_]+",
                                caseMode = FormField.CaseMode.LOWER_CASE
                            )
                        ),
                        errorCodes = mapOf(
                            ERROR_LOGIN_LIBRUS_API_INVALID_LOGIN to R.string.login_error_incorrect_code_or_pin,
                            ERROR_LOGIN_LIBRUS_API_INVALID_REQUEST to R.string.login_error_incorrect_code_or_pin
                        )
                    )
                )
            ),
            Register(
                loginType = LoginType.USOS,
                registerName = R.string.login_type_usos,
                registerLogo = R.drawable.login_logo_usos,
                loginModes = listOf(
                    Mode(
                        loginMode = LoginMode.USOS_OAUTH,
                        name = R.string.login_mode_usos_oauth,
                        icon = R.drawable.login_mode_usos_api,
                        guideText = R.string.login_mode_usos_oauth_guide,
                        isPlatformSelection = true,
                        credentials = listOf(),
                        errorCodes = mapOf(),
                    ),
                ),
            ),
            Register(
                loginType = LoginType.DEMO,
                registerName = R.string.login_type_demo,
                registerLogo = R.mipmap.ic_launcher,
                loginModes = listOf(
                    Mode(
                        loginMode = LoginMode.DEMO,
                        name = R.string.login_mode_demo,
                        icon = R.mipmap.ic_launcher,
                        guideText = R.string.login_mode_demo,
                        credentials = listOf(),
                        errorCodes = mapOf(),
                    ),
                ),
            ),
        )
    }

    data class Register(
        val loginType: LoginType,
        val registerName: Int,
        @DrawableRes
        val registerLogo: Int,

        val loginModes: List<Mode>
    ) : ExpandableItemModel<Mode>(loginModes.toMutableList()) {
        override var level = 1
    }

    data class Mode(
        val loginMode: LoginMode,

        @StringRes
        val name: Int,
        @DrawableRes
        val icon: Int,
        @StringRes
        val hintText: Int? = null,
        @StringRes
        val guideText: Int,

        val isRecommended: Boolean = false,
        val isTesting: Boolean = false,
        val isDevOnly: Boolean = false,
        val isPlatformSelection: Boolean = false,

        val credentials: List<BaseCredential>,
        val errorCodes: Map<Int, Int>
    )

    data class Platform(
        val id: Int,
        val name: String,
        val description: String?,
        val icon: String,
        val screenshot: String?,
        val formFields: List<String>,
        val data: JsonObject,
        val storeKey: String?,
    )

    open class BaseCredential(
        open val keyName: String,
        @StringRes
        open val name: Int,
        open val errorCodes: Map<Int, Int>
    )

    data class FormField(
        override val keyName: String,

        @StringRes
        override val name: Int,
        val icon: IIcon,
        @StringRes
        val placeholder: Int? = null,
        @StringRes
        val emptyText: Int,
        @StringRes
        val invalidText: Int,
        override val errorCodes: Map<Int, Int>,
        @StringRes
        val hintText: Int? = null,
        @StringRes
        val prefix: Int? = null,
        @StringRes
        val suffix: Int? = null,

        val isRequired: Boolean = true,
        val validationRegex: String,
        val caseMode: CaseMode = CaseMode.UNCHANGED,
        val hideText: Boolean = false,
        val isNumber: Boolean = false,
        val stripTextRegex: String? = null,
        val qrDecoderClass: Class<out LoginQrDecoder>? = null,
    ) : BaseCredential(keyName, name, errorCodes) {
        enum class CaseMode { UNCHANGED, UPPER_CASE, LOWER_CASE }
    }

    data class FormCheckbox(
        override val keyName: String,
        @StringRes
        override val name: Int,
        val checked: Boolean = false,
        override val errorCodes: Map<Int, Int> = mapOf()
    ) : BaseCredential(keyName, name, errorCodes)

    var chooserList: MutableList<Any>? = null
    var platformList: MutableMap<Int, List<Platform>> = mutableMapOf()
}
